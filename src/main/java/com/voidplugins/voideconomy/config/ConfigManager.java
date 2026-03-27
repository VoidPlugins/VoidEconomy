package com.voidplugins.voideconomy.config;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.Currency;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final VoidEconomy plugin;

    public ConfigManager(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadCurrencies() {
        ConfigurationSection cs = plugin.getConfig().getConfigurationSection("currencies");
        if (cs == null) {
            plugin.getLogger().warning("No 'currencies' section found in config.yml!");
            return;
        }

        for (String id : cs.getKeys(false)) {
            ConfigurationSection cur = cs.getConfigurationSection(id);
            if (cur == null) continue;

            String displayName = cur.getString("display-name", id);
            String symbol = cur.getString("symbol", "");
            String command = cur.getString("command", id).toLowerCase();
            double defaultBalance = cur.getDouble("default-balance", 0.0);
            double maxBalance = cur.getDouble("max-balance", -1);
            int decimals = cur.getInt("decimals", 2);

            Map<String, String> messages = new HashMap<>();
            ConfigurationSection msgSection = cur.getConfigurationSection("messages");
            if (msgSection != null) {
                for (String key : msgSection.getKeys(false)) {
                    messages.put(key, msgSection.getString(key, ""));
                }
            }

            Currency currency = new Currency(id, displayName, symbol, command,
                    defaultBalance, maxBalance, decimals, messages);
            plugin.getCurrencyManager().register(currency);
            plugin.getLogger().info("Registered currency: " + id + " (/" + command + ")");
        }
    }

    public String getGlobalMessage(String key) {
        return plugin.getConfig().getString("messages." + key, "&cMessage not found: " + key);
    }

    public int getAutoSaveInterval() {
        return plugin.getConfig().getInt("settings.auto-save-interval", 300);
    }

    public int getTopCacheRefresh() {
        return plugin.getConfig().getInt("settings.top-cache-refresh", 300);
    }

    public String getDatabaseFile() {
        return plugin.getConfig().getString("database.file", "economy.db");
    }
}
