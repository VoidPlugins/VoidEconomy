package com.voidplugins.voideconomy.listener;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import com.voidplugins.voideconomy.withdraw.WithdrawItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WithdrawListener implements Listener {

    private final VoidEconomy plugin;

    public WithdrawListener(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only trigger on right-click, main hand, with an item
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!WithdrawItem.isWithdrawItem(plugin, item)) return;

        event.setCancelled(true);

        String currencyId = WithdrawItem.getCurrency(plugin, item);
        double amount     = WithdrawItem.getAmount(plugin, item);

        Currency currency = plugin.getCurrencyManager().getById(currencyId);
        if (currency == null) {
            player.sendMessage(MessageUtil.colorize("&cThis note references an unknown currency."));
            return;
        }

        // Remove one note from hand
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Deposit balance
        plugin.getAPI().give(player.getUniqueId(), currencyId, amount).thenAccept(newBalance ->
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(MessageUtil.colorize(
                                applyPlaceholders(currency.getMessage("deposit-success"), currency, amount)))));
    }

    private String applyPlaceholders(String msg, Currency currency, double amount) {
        return msg.replace("{symbol}", currency.getSymbol())
                  .replace("{display-name}", currency.getDisplayName())
                  .replace("{currency}", currency.getId())
                  .replace("{amount}", currency.formatAmount(amount))
                  .replace("{formatted}", MessageUtil.formatCompact(amount));
    }
}
