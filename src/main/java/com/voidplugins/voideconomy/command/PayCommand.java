package com.voidplugins.voideconomy.command;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final VoidEconomy plugin;

    public PayCommand(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("console-must-specify-player")));
            return true;
        }

        if (!payer.hasPermission("voideconomy.pay")) {
            payer.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("no-permission")));
            return true;
        }

        if (args.length < 3) {
            payer.sendMessage(MessageUtil.colorize("&cUsage: /pay <player> <currency> <amount>"));
            return true;
        }

        String targetName = args[0];
        String currencyId = args[1].toLowerCase();
        double amount = MessageUtil.parseAmount(args[2]);

        if (amount <= 0) {
            payer.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("invalid-amount")));
            return true;
        }

        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        if (currency == null) {
            payer.sendMessage(MessageUtil.colorize("&cUnknown currency: &e" + currencyId));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            payer.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("player-not-found")
                            .replace("{player}", targetName)));
            return true;
        }

        if (target.equals(payer)) {
            payer.sendMessage(MessageUtil.colorize("&cYou cannot pay yourself."));
            return true;
        }

        plugin.getAPI().has(payer.getUniqueId(), currencyId, amount).thenAccept(hasEnough -> {
            if (!hasEnough) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        payer.sendMessage(MessageUtil.colorize("&cYou don't have enough &e"
                                + currency.getDisplayName() + "&c.")));
                return;
            }

            plugin.getAPI().remove(payer.getUniqueId(), currencyId, amount).thenCompose(payerBalance ->
                    plugin.getAPI().give(target.getUniqueId(), currencyId, amount)
            ).thenAccept(targetBalance -> Bukkit.getScheduler().runTask(plugin, () -> {
                String compact = MessageUtil.formatCompact(amount);
                payer.sendMessage(MessageUtil.colorize(
                        "&aPaid &6" + currency.getSymbol() + currency.formatAmount(amount)
                        + " &7(" + compact + ")&a to &6" + target.getName() + "&a."));
                target.sendMessage(MessageUtil.colorize(
                        "&6" + payer.getName() + " &apaid you &6"
                        + currency.getSymbol() + currency.formatAmount(amount)
                        + " &7(" + compact + ")&a."));
            }));
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) result.add(p.getName());
            }
            return result;
        }
        if (args.length == 2) {
            List<String> result = new ArrayList<>();
            String input = args[1].toLowerCase();
            for (Currency c : plugin.getCurrencyManager().getCurrencies()) {
                if (c.getId().startsWith(input)) result.add(c.getId());
            }
            return result;
        }
        if (args.length == 3) {
            return List.of("1", "100", "1k", "10k", "1m");
        }
        return List.of();
    }
}
