package com.voidplugins.voideconomy.placeholder;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry;
import com.voidplugins.voideconomy.store.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Placeholder format: %voideconomy_<placeholder>%
 *
 * Available placeholders:
 *   %voideconomy_<currency>%                       → own balance (formatted)
 *   %voideconomy_<currency>_raw%                   → own balance (raw number)
 *   %voideconomy_<currency>_top_<rank>_name%       → name of rank-N player
 *   %voideconomy_<currency>_top_<rank>_balance%    → balance of rank-N player (formatted)
 *   %voideconomy_<currency>_top_<rank>_balance_raw% → balance of rank-N player (raw)
 */
public class VoidEconomyExpansion extends PlaceholderExpansion {

    private final VoidEconomy plugin;

    public VoidEconomyExpansion(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "voideconomy"; }
    @Override public @NotNull String getAuthor() { return "VoidPlugins"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // params is the part after "voideconomy_"
        String[] parts = params.split("_", -1);
        if (parts.length == 0) return null;

        String currencyId = parts[0];
        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        if (currency == null) return null;

        // %voideconomy_<currency>%  or  %voideconomy_<currency>_raw%
        if (parts.length == 1) {
            return getFormattedBalance(player, currency, false);
        }

        if (parts.length == 2 && parts[1].equals("raw")) {
            return getFormattedBalance(player, currency, true);
        }

        // %voideconomy_<currency>_top_<rank>_<field>%
        if (parts.length >= 4 && parts[1].equals("top")) {
            int rank;
            try {
                rank = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
            String field = parts[3];
            boolean raw = parts.length >= 5 && parts[4].equals("raw");

            List<TopEntry> top = plugin.getCurrencyManager().getTopCache(currencyId);
            TopEntry entry = top.stream().filter(e -> e.rank() == rank).findFirst().orElse(null);
            if (entry == null) return rank <= 10 ? "N/A" : null;

            return switch (field) {
                case "name"    -> entry.playerName();
                case "balance" -> raw ? String.valueOf(entry.balance()) : currency.formatAmount(entry.balance());
                default        -> null;
            };
        }

        return null;
    }

    private String getFormattedBalance(OfflinePlayer player, Currency currency, boolean raw) {
        if (player == null) return "0";

        // Try cache first (fast path for online players)
        PlayerData data = plugin.getPlayerStore().getCached(player.getUniqueId());
        if (data != null) {
            double balance = data.getBalance(currency.getId(), currency.getDefaultBalance());
            return raw ? String.valueOf(balance) : currency.formatAmount(balance);
        }

        // For offline players, PlaceholderAPI may call this — return default
        return raw ? String.valueOf(currency.getDefaultBalance())
                   : currency.formatAmount(currency.getDefaultBalance());
    }
}
