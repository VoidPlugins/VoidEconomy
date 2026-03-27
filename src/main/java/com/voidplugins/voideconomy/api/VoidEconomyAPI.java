package com.voidplugins.voideconomy.api;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.api.event.CurrencyChangeEvent;
import com.voidplugins.voideconomy.currency.Currency;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * VoidEconomy Developer API
 *
 * <p>How to use in your plugin:
 * <pre>{@code
 * // In your plugin's onEnable, after VoidEconomy has loaded:
 * VoidEconomyAPI api = VoidEconomyAPI.getInstance();
 *
 * // Give a player 100 money (async)
 * api.give(player.getUniqueId(), "money", 100).thenAccept(newBalance -> {
 *     Bukkit.getScheduler().runTask(yourPlugin, () ->
 *         player.sendMessage("New balance: " + newBalance));
 * });
 *
 * // Or block on the main thread if you must (not recommended):
 * double balance = api.getBalance(player.getUniqueId(), "money").join();
 * }</pre>
 *
 * <p>All operations return {@link CompletableFuture} and are safe to call from any thread.
 * For online players, reads are instant (cache). Writes for online players update the
 * in-memory cache immediately and are flushed to the database on the auto-save interval
 * or when the player disconnects.
 *
 * <p>Add VoidEconomy as a depend/softdepend in your plugin.yml to ensure correct load order.
 */
public final class VoidEconomyAPI {

    private static VoidEconomyAPI instance;
    private final VoidEconomy plugin;

    VoidEconomyAPI(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    static void init(VoidEconomy plugin) {
        instance = new VoidEconomyAPI(plugin);
    }

    /**
     * Returns the shared API instance.
     *
     * @return the API instance, or {@code null} if VoidEconomy is not loaded
     */
    public static VoidEconomyAPI getInstance() {
        return instance;
    }

    // ── Currency lookup ───────────────────────────────────────────────────────

    /**
     * Returns true if a currency with the given ID is configured.
     *
     * @param currencyId the currency ID (e.g. "money", "tokens")
     */
    public boolean hasCurrency(String currencyId) {
        return plugin.getCurrencyManager().getById(currencyId) != null;
    }

    /**
     * Returns the {@link Currency} object for a given ID, or {@code null} if not found.
     */
    public Currency getCurrency(String currencyId) {
        return plugin.getCurrencyManager().getById(currencyId);
    }

    /**
     * Returns all registered currencies.
     */
    public Collection<Currency> getCurrencies() {
        return plugin.getCurrencyManager().getCurrencies();
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    /**
     * Gets a player's balance for the given currency.
     *
     * <p>Uses the in-memory cache for online players (instant), queries the database
     * asynchronously for offline players.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @return a future that resolves to the balance (or the currency's default if not found)
     * @throws IllegalArgumentException if the currency ID is unknown
     */
    public CompletableFuture<Double> getBalance(UUID uuid, String currencyId) {
        Currency currency = requireCurrency(currencyId);
        String name = resolveName(uuid);
        return plugin.getPlayerStore().getBalance(uuid, name, currencyId);
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Double> getBalance(OfflinePlayer player, String currencyId) {
        return getBalance(player.getUniqueId(), currencyId);
    }

    // ── Give / Add ────────────────────────────────────────────────────────────

    /**
     * Adds {@code amount} to a player's balance, capped at the currency's max-balance.
     *
     * <p>Fires a {@link CurrencyChangeEvent} that can be cancelled by other plugins.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @param amount     a positive amount to add
     * @return a future resolving to the new balance after the addition
     * @throws IllegalArgumentException if amount ≤ 0 or the currency is unknown
     */
    public CompletableFuture<Double> give(UUID uuid, String currencyId, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        Currency currency = requireCurrency(currencyId);
        String name = resolveName(uuid);

        return getBalance(uuid, currencyId).thenCompose(oldBalance -> {
            double newBalance = oldBalance + amount;
            if (currency.hasMaxBalance() && newBalance > currency.getMaxBalance()) {
                newBalance = currency.getMaxBalance();
            }
            final double finalBalance = newBalance;
            CurrencyChangeEvent event = fireEvent(uuid, name, currency, oldBalance, finalBalance,
                    CurrencyChangeEvent.Reason.GIVE);
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(oldBalance);
            }
            return plugin.getPlayerStore()
                    .setBalance(uuid, name, currencyId, event.getNewBalance())
                    .thenApply(v -> event.getNewBalance());
        });
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Double> give(OfflinePlayer player, String currencyId, double amount) {
        return give(player.getUniqueId(), currencyId, amount);
    }

    // ── Remove / Take ─────────────────────────────────────────────────────────

    /**
     * Subtracts {@code amount} from a player's balance (floor at 0).
     *
     * <p>Fires a {@link CurrencyChangeEvent} that can be cancelled by other plugins.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @param amount     a positive amount to subtract
     * @return a future resolving to the new balance after the subtraction
     * @throws IllegalArgumentException if amount ≤ 0 or the currency is unknown
     */
    public CompletableFuture<Double> remove(UUID uuid, String currencyId, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        Currency currency = requireCurrency(currencyId);
        String name = resolveName(uuid);

        return getBalance(uuid, currencyId).thenCompose(oldBalance -> {
            double newBalance = Math.max(0, oldBalance - amount);
            CurrencyChangeEvent event = fireEvent(uuid, name, currency, oldBalance, newBalance,
                    CurrencyChangeEvent.Reason.REMOVE);
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(oldBalance);
            }
            return plugin.getPlayerStore()
                    .setBalance(uuid, name, currencyId, event.getNewBalance())
                    .thenApply(v -> event.getNewBalance());
        });
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Double> remove(OfflinePlayer player, String currencyId, double amount) {
        return remove(player.getUniqueId(), currencyId, amount);
    }

    // ── Set ───────────────────────────────────────────────────────────────────

    /**
     * Sets a player's balance to an exact value.
     *
     * <p>Fires a {@link CurrencyChangeEvent} that can be cancelled by other plugins.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @param amount     the new balance (≥ 0)
     * @return a future that completes when the change is applied
     * @throws IllegalArgumentException if amount < 0 or the currency is unknown
     */
    public CompletableFuture<Void> set(UUID uuid, String currencyId, double amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be ≥ 0, got: " + amount);
        Currency currency = requireCurrency(currencyId);
        String name = resolveName(uuid);

        return getBalance(uuid, currencyId).thenCompose(oldBalance -> {
            CurrencyChangeEvent event = fireEvent(uuid, name, currency, oldBalance, amount,
                    CurrencyChangeEvent.Reason.SET);
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(null);
            }
            return plugin.getPlayerStore().setBalance(uuid, name, currencyId, event.getNewBalance());
        });
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Void> set(OfflinePlayer player, String currencyId, double amount) {
        return set(player.getUniqueId(), currencyId, amount);
    }

    // ── Clear (set to 0) ──────────────────────────────────────────────────────

    /**
     * Sets a player's balance to 0.
     *
     * <p>Fires a {@link CurrencyChangeEvent} that can be cancelled by other plugins.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @return a future that completes when the change is applied
     * @throws IllegalArgumentException if the currency is unknown
     */
    public CompletableFuture<Void> clear(UUID uuid, String currencyId) {
        return set(uuid, currencyId, 0.0);
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Void> clear(OfflinePlayer player, String currencyId) {
        return clear(player.getUniqueId(), currencyId);
    }

    // ── Reset (set to default) ────────────────────────────────────────────────

    /**
     * Resets a player's balance to the currency's configured default.
     *
     * <p>Fires a {@link CurrencyChangeEvent} that can be cancelled by other plugins.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @return a future that completes when the change is applied
     * @throws IllegalArgumentException if the currency is unknown
     */
    public CompletableFuture<Void> reset(UUID uuid, String currencyId) {
        Currency currency = requireCurrency(currencyId);
        return set(uuid, currencyId, currency.getDefaultBalance());
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Void> reset(OfflinePlayer player, String currencyId) {
        return reset(player.getUniqueId(), currencyId);
    }

    // ── Has enough ────────────────────────────────────────────────────────────

    /**
     * Returns a future resolving to {@code true} if the player has at least {@code amount}.
     *
     * @param uuid       the player UUID
     * @param currencyId the currency ID
     * @param amount     the minimum balance to check for
     */
    public CompletableFuture<Boolean> has(UUID uuid, String currencyId, double amount) {
        return getBalance(uuid, currencyId).thenApply(balance -> balance >= amount);
    }

    /**
     * Convenience overload using an {@link OfflinePlayer}.
     */
    public CompletableFuture<Boolean> has(OfflinePlayer player, String currencyId, double amount) {
        return has(player.getUniqueId(), currencyId, amount);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Currency requireCurrency(String currencyId) {
        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        if (currency == null) {
            throw new IllegalArgumentException("Unknown currency: '" + currencyId
                    + "'. Available: " + plugin.getCurrencyManager().getCurrencies()
                    .stream().map(Currency::getId).toList());
        }
        return currency;
    }

    private String resolveName(UUID uuid) {
        // Check cache first (online players)
        var cached = plugin.getPlayerStore().getCached(uuid);
        if (cached != null) return cached.getUsername();
        // Fall back to Bukkit's offline cache
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(uuid.toString()) != null
                ? Bukkit.getOfflinePlayer(uuid) : null;
        return (op != null && op.getName() != null) ? op.getName() : "Unknown";
    }

    /**
     * Fires a {@link CurrencyChangeEvent} on the main thread (required for Bukkit events).
     * Blocks briefly if called from an async thread — acceptable since the event itself is fast.
     */
    private CurrencyChangeEvent fireEvent(UUID uuid, String name, Currency currency,
                                          double oldBalance, double newBalance,
                                          CurrencyChangeEvent.Reason reason) {
        CurrencyChangeEvent event = new CurrencyChangeEvent(uuid, name, currency, oldBalance, newBalance, reason);
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
        } else {
            // Schedule on main thread and wait — necessary because Bukkit events must be
            // called synchronously, and callers need to see whether the event was cancelled.
            try {
                Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    Bukkit.getPluginManager().callEvent(event);
                    return null;
                }).get();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fire CurrencyChangeEvent: " + e.getMessage());
            }
        }
        return event;
    }
}
