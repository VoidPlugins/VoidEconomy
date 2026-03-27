package com.voidplugins.voideconomy.command;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import com.voidplugins.voideconomy.withdraw.WithdrawItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class WithdrawCommand implements CommandExecutor, TabCompleter {

    private final VoidEconomy plugin;

    public WithdrawCommand(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("console-must-specify-player")));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.colorize("&cUsage: /withdraw <currency> <amount>"));
            return true;
        }

        Currency currency = plugin.getCurrencyManager().getById(args[0]);
        if (currency == null) {
            player.sendMessage(MessageUtil.colorize("&cUnknown currency: &e" + args[0]));
            return true;
        }

        if (!plugin.getConfig().getBoolean("currencies." + currency.getId() + ".withdraw.enabled", false)) {
            player.sendMessage(MessageUtil.colorize(applyPlaceholders(
                    currency.getMessage("withdraw-disabled"), currency, 0)));
            return true;
        }

        if (!player.hasPermission("voideconomy." + currency.getId() + ".withdraw")) {
            player.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("no-permission")));
            return true;
        }

        double amount = MessageUtil.parseAmount(args[1]);
        if (amount <= 0) {
            player.sendMessage(MessageUtil.colorize(
                    plugin.getConfigManager().getGlobalMessage("invalid-amount")));
            return true;
        }

        plugin.getAPI().has(player.getUniqueId(), currency.getId(), amount).thenAccept(hasEnough -> {
            if (!hasEnough) {
                player.sendMessage(MessageUtil.colorize(
                        applyPlaceholders(currency.getMessage("withdraw-not-enough"), currency, amount)));
                return;
            }

            plugin.getAPI().remove(player.getUniqueId(), currency.getId(), amount).thenAccept(newBalance -> {
                ItemStack item = WithdrawItem.create(plugin, currency, amount);

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getInventory().firstEmpty() == -1) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        player.sendMessage(MessageUtil.colorize(
                                plugin.getConfigManager().getGlobalMessage("withdraw-inventory-full")));
                    } else {
                        player.getInventory().addItem(item);
                    }
                    player.sendMessage(MessageUtil.colorize(
                            applyPlaceholders(currency.getMessage("withdraw-success"), currency, amount)));
                });
            });
        });

        return true;
    }

    private String applyPlaceholders(String msg, Currency currency, double amount) {
        return msg.replace("{symbol}", currency.getSymbol())
                  .replace("{display-name}", currency.getDisplayName())
                  .replace("{currency}", currency.getId())
                  .replace("{amount}", currency.formatAmount(amount))
                  .replace("{formatted}", MessageUtil.formatCompact(amount));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (Currency c : plugin.getCurrencyManager().getCurrencies()) {
                if (c.getId().startsWith(input)
                        && plugin.getConfig().getBoolean("currencies." + c.getId() + ".withdraw.enabled", false)
                        && (!(sender instanceof Player p) || p.hasPermission("voideconomy." + c.getId() + ".withdraw"))) {
                    result.add(c.getId());
                }
            }
            return result;
        }
        if (args.length == 2) {
            return List.of("100", "1k", "10k", "100k", "1m");
        }
        return List.of();
    }
}
