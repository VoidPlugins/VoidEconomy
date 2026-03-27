package com.voidplugins.voideconomy.withdraw;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class WithdrawItem {

    public static final String PDC_CURRENCY = "withdraw_currency";
    public static final String PDC_AMOUNT   = "withdraw_amount";

    private WithdrawItem() {}

    public static ItemStack create(VoidEconomy plugin, Currency currency, double amount) {
        String path = "currencies." + currency.getId() + ".withdraw.";

        String materialName = plugin.getConfig().getString(path + "material", "PAPER").toUpperCase();
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' for currency " + currency.getId() + ", defaulting to PAPER.");
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Display name
        String name = plugin.getConfig().getString(path + "name", "&f{symbol}{amount}");
        meta.setDisplayName(MessageUtil.colorize(applyPlaceholders(name, currency, amount)));

        // Lore
        List<String> configLore = plugin.getConfig().getStringList(path + "lore");
        List<String> lore = new ArrayList<>();
        for (String line : configLore) {
            lore.add(MessageUtil.colorize(applyPlaceholders(line, currency, amount)));
        }
        meta.setLore(lore);

        // Custom model data
        int modelData = plugin.getConfig().getInt(path + "custom-model-data", 0);
        if (modelData > 0) meta.setCustomModelData(modelData);

        // Store currency and amount in NBT so we can identify and redeem the item
        NamespacedKey keyCurrency = new NamespacedKey(plugin, PDC_CURRENCY);
        NamespacedKey keyAmount   = new NamespacedKey(plugin, PDC_AMOUNT);
        meta.getPersistentDataContainer().set(keyCurrency, PersistentDataType.STRING, currency.getId());
        meta.getPersistentDataContainer().set(keyAmount,   PersistentDataType.DOUBLE, amount);

        item.setItemMeta(meta);
        return item;
    }

    /** Returns true if this item is a VoidEconomy withdraw note. */
    public static boolean isWithdrawItem(VoidEconomy plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, PDC_CURRENCY);
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public static String getCurrency(VoidEconomy plugin, ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_CURRENCY), PersistentDataType.STRING);
    }

    public static double getAmount(VoidEconomy plugin, ItemStack item) {
        Double value = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_AMOUNT), PersistentDataType.DOUBLE);
        return value != null ? value : 0;
    }

    private static String applyPlaceholders(String text, Currency currency, double amount) {
        return text.replace("{symbol}", currency.getSymbol())
                   .replace("{display-name}", currency.getDisplayName())
                   .replace("{currency}", currency.getId())
                   .replace("{amount}", currency.formatAmount(amount))
                   .replace("{formatted}", MessageUtil.formatCompact(amount));
    }
}
