package com.voidplugins.voideconomy.command;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class VoidEconomyCommand implements CommandExecutor, TabCompleter {

    private final VoidEconomy plugin;

    public VoidEconomyCommand(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("voideconomy.reload")) {
                sender.sendMessage(MessageUtil.colorize(
                        plugin.getConfigManager().getGlobalMessage("no-permission")));
                return true;
            }

            plugin.reload();
            sender.sendMessage(MessageUtil.colorize("&aVoidEconomy reloaded successfully."));
            return true;
        }

        if (!sender.hasPermission("voideconomy.info")) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("no-permission")));
            return true;
        }

        sender.sendMessage(MessageUtil.colorize("&8&m                              "));
        sender.sendMessage(MessageUtil.colorize("  &6VoidEconomy &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(MessageUtil.colorize("  &7Currencies:"));

        for (Currency currency : plugin.getCurrencyManager().getCurrencies()) {
            sender.sendMessage(MessageUtil.colorize(
                    "  &f" + currency.getId()
                    + " &7(" + currency.getSymbol() + ")"
                    + " &8→ &f/" + currency.getCommand()
                    + " &7| &f/" + currency.getCommand() + "top"
            ));
        }

        sender.sendMessage(MessageUtil.colorize("  &7Use &f/voideconomy reload &7to reload the config."));
        sender.sendMessage(MessageUtil.colorize("&8&m                              "));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("voideconomy.reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return List.of("reload");
            }
        }
        return List.of();
    }
}
