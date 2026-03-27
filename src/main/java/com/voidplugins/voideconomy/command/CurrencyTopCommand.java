package com.voidplugins.voideconomy.command;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry;
import com.voidplugins.voideconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class CurrencyTopCommand extends Command {

    private static final int TOP_LIMIT = 10;

    private final VoidEconomy plugin;
    private final Currency currency;

    public CurrencyTopCommand(VoidEconomy plugin, Currency currency) {
        super(currency.getCommand() + "top");
        this.plugin = plugin;
        this.currency = currency;
        setDescription("View the top " + TOP_LIMIT + " richest players for " + currency.getDisplayName());
        setPermission("voideconomy." + currency.getId() + ".top");
        setUsage("/" + currency.getCommand() + "top");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("voideconomy." + currency.getId() + ".top")) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("no-permission")));
            return true;
        }

        // Flush online player data to DB before querying so the list is accurate,
        // then refresh the cache and display it.
        plugin.getPlayerStore().refreshTopCache(currency.getId());

        // Wait a tick for the async refresh to complete, then send from cache
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            List<TopEntry> entries = plugin.getCurrencyManager().getTopCache(currency.getId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (entries.isEmpty()) {
                    sender.sendMessage(MessageUtil.colorize(
                            formatMsg(currency.getMessage("top-empty"))));
                    return;
                }

                sender.sendMessage(MessageUtil.colorize(formatMsg(currency.getMessage("top-header"))));
                for (TopEntry entry : entries) {
                    String line = currency.getMessage("top-entry")
                            .replace("{rank}", String.valueOf(entry.rank()))
                            .replace("{player}", entry.playerName())
                            .replace("{amount}", currency.formatAmount(entry.balance()))
                            .replace("{formatted}", MessageUtil.formatCompact(entry.balance()))
                            .replace("{symbol}", currency.getSymbol())
                            .replace("{display-name}", currency.getDisplayName());
                    sender.sendMessage(MessageUtil.colorize(line));
                }
                sender.sendMessage(MessageUtil.colorize(formatMsg(currency.getMessage("top-footer"))));
            });
        }, 20L); // 1 second wait for the async DB query to complete

        return true;
    }

    private String formatMsg(String raw) {
        return raw.replace("{limit}", String.valueOf(TOP_LIMIT))
                  .replace("{display-name}", currency.getDisplayName())
                  .replace("{symbol}", currency.getSymbol());
    }
}
