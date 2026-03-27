package com.voidplugins.voideconomy.placeholder;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry;
import com.voidplugins.voideconomy.store.PlayerData;
import com.voidplugins.voideconomy.util.MessageUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * %voideconomy_<currency>%                              → balance (formatted)
 * %voideconomy_<currency>_raw%                          → balance (raw double)
 * %voideconomy_<currency>_formatted%                    → balance (compact: 1.5K, 2.3M, ...)
 * %voideconomy_<currency>_top_<rank>_name%              → top-N player name
 * %voideconomy_<currency>_top_<rank>_balance%           → top-N balance (formatted)
 * %voideconomy_<currency>_top_<rank>_balance_raw%       → top-N balance (raw double)
 * %voideconomy_<currency>_top_<rank>_balance_formatted% → top-N balance (compact)
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
        String[] parts = params.split("_", -1);
        if (parts.length == 0) return null;

        String currencyId = parts[0];
        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        if (currency == null) return null;

        // %voideconomy_<currency>%
        if (parts.length == 1) {
            return balance(player, currency, Format.NORMAL);
        }

        // %voideconomy_<currency>_raw% / %voideconomy_<currency>_formatted%
        if (parts.length == 2) {
            return switch (parts[1]) {
                case "raw"       -> balance(player, currency, Format.RAW);
                case "formatted" -> balance(player, currency, Format.COMPACT);
                default          -> null;
            };
        }

        // %voideconomy_<currency>_top_<rank>_<field>[_raw|_formatted]%
        if (parts.length >= 4 && parts[1].equals("top")) {
            int rank;
            try {
                rank = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }

            List<TopEntry> top = plugin.getCurrencyManager().getTopCache(currencyId);
            TopEntry entry = top.stream().filter(e -> e.rank() == rank).findFirst().orElse(null);
            if (entry == null) return rank <= 10 ? "N/A" : null;

            String field = parts[3];
            String modifier = parts.length >= 5 ? parts[4] : "";

            return switch (field) {
                case "name" -> entry.playerName();
                case "balance" -> switch (modifier) {
                    case "raw"       -> String.valueOf(entry.balance());
                    case "formatted" -> MessageUtil.formatCompact(entry.balance());
                    default          -> currency.formatAmount(entry.balance());
                };
                default -> null;
            };
        }

        return null;
    }

    private String balance(OfflinePlayer player, Currency currency, Format format) {
        if (player == null) return fallback(currency, format);

        PlayerData data = plugin.getPlayerStore().getCached(player.getUniqueId());
        double balance = data != null
                ? data.getBalance(currency.getId(), currency.getDefaultBalance())
                : currency.getDefaultBalance();

        return switch (format) {
            case RAW     -> String.valueOf(balance);
            case COMPACT -> MessageUtil.formatCompact(balance);
            case NORMAL  -> currency.formatAmount(balance);
        };
    }

    private String fallback(Currency currency, Format format) {
        double def = currency.getDefaultBalance();
        return switch (format) {
            case RAW     -> String.valueOf(def);
            case COMPACT -> MessageUtil.formatCompact(def);
            case NORMAL  -> currency.formatAmount(def);
        };
    }

    private enum Format { NORMAL, RAW, COMPACT }
}
