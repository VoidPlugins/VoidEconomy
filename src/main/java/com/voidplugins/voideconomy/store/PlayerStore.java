package com.voidplugins.voideconomy.store;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerStore {

    private final VoidEconomy plugin;
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerStore(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public CompletableFuture<Void> loadPlayer(UUID uuid, String username) {
        return plugin.getDatabaseManager().loadBalances(uuid).thenAccept(balances -> {
            PlayerData data = new PlayerData(uuid, username);
            data.loadBalances(balances);

            // Ensure every configured currency has at least a default entry
            for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
                data.getBalances().putIfAbsent(currency.getId(), currency.getDefaultBalance());
            }
            cache.put(uuid, data);
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data == null) return CompletableFuture.completedFuture(null);
        return plugin.getDatabaseManager().saveBalances(data.getUuid(), data.getUsername(), data.getBalances())
                .thenRun(data::clearDirty);
    }

    /** Flush all dirty cached players to the DB (non-blocking, fires & forgets). */
    public void saveAll() {
        List<PlayerData> dirty = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) dirty.add(data);
        }
        if (dirty.isEmpty()) return;

        plugin.getLogger().info("Flushing " + dirty.size() + " dirty player(s) to database...");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PlayerData data : dirty) {
            CompletableFuture<Void> f = plugin.getDatabaseManager()
                    .saveBalances(data.getUuid(), data.getUsername(), data.getBalances())
                    .thenRun(data::clearDirty);
            futures.add(f);
        }
        // Block until all saves complete (important for shutdown)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void startAutoSave() {
        int interval = plugin.getConfigManager().getAutoSaveInterval() * 20L > 0
                ? plugin.getConfigManager().getAutoSaveInterval() * 20
                : 20 * 300;

        // Auto-save task (async)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (PlayerData data : cache.values()) {
                if (data.isDirty()) {
                    plugin.getDatabaseManager()
                            .saveBalances(data.getUuid(), data.getUsername(), data.getBalances())
                            .thenRun(data::clearDirty);
                }
            }
        }, interval, interval);

        // Top-cache refresh task (async)
        int topRefresh = plugin.getConfigManager().getTopCacheRefresh() * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
                refreshTopCache(currency.getId());
            }
        }, topRefresh, topRefresh);
    }

    public void refreshTopCache(String currencyId) {
        plugin.getDatabaseManager().getTop(currencyId, 10).thenAccept(entries -> {
            // Merge with online player balances so the list is always up-to-date
            Currency currency = plugin.getCurrencyManager().getById(currencyId);
            if (currency != null) {
                // Replace any cached entry for an online player with their live balance
                for (int i = 0; i < entries.size(); i++) {
                    var entry = entries.get(i);
                    org.bukkit.entity.Player online = Bukkit.getPlayerExact(entry.playerName());
                    if (online != null) {
                        PlayerData pd = cache.get(online.getUniqueId());
                        if (pd != null) {
                            entries.set(i, new com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry(
                                    entry.rank(), entry.playerName(),
                                    pd.getBalance(currencyId, currency.getDefaultBalance())));
                        }
                    }
                }
                // Re-sort after merge
                entries.sort((a, b) -> Double.compare(b.balance(), a.balance()));
                for (int i = 0; i < entries.size(); i++) {
                    var e = entries.get(i);
                    entries.set(i, new com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry(i + 1, e.playerName(), e.balance()));
                }
            }
            plugin.getCurrencyManager().updateTopCache(currencyId, entries);
        });
    }

    // ── Balance access ────────────────────────────────────────────────────────

    /** Get balance. Uses cache for online players, queries DB for offline. */
    public CompletableFuture<Double> getBalance(UUID uuid, String username, String currencyId) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            Currency currency = plugin.getCurrencyManager().getById(currencyId);
            double def = currency != null ? currency.getDefaultBalance() : 0.0;
            return CompletableFuture.completedFuture(data.getBalance(currencyId, def));
        }
        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        double def = currency != null ? currency.getDefaultBalance() : 0.0;
        return plugin.getDatabaseManager().getBalance(uuid, currencyId, def);
    }

    /** Set balance. Updates cache for online players, writes DB directly for offline. */
    public CompletableFuture<Void> setBalance(UUID uuid, String username, String currencyId, double amount) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.setBalance(currencyId, amount);
            if (username != null) data.setUsername(username);
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getDatabaseManager().setBalance(uuid, username, currencyId, amount);
    }

    /** Add to balance, respecting the max-balance cap. Returns new balance. */
    public CompletableFuture<Double> addBalance(UUID uuid, String username, String currencyId, double amount, Currency currency) {
        return getBalance(uuid, username, currencyId).thenCompose(current -> {
            double newBalance = current + amount;
            if (currency.hasMaxBalance() && newBalance > currency.getMaxBalance()) {
                newBalance = currency.getMaxBalance();
            }
            final double finalBalance = newBalance;
            return setBalance(uuid, username, currencyId, finalBalance).thenApply(v -> finalBalance);
        });
    }

    /** Subtract from balance (floor at 0). Returns new balance. */
    public CompletableFuture<Double> subtractBalance(UUID uuid, String username, String currencyId, double amount) {
        return getBalance(uuid, username, currencyId).thenCompose(current -> {
            double newBalance = Math.max(0, current - amount);
            return setBalance(uuid, username, currencyId, newBalance).thenApply(v -> newBalance);
        });
    }

    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }
}
