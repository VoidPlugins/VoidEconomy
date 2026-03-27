package com.voidplugins.voideconomy;

import com.voidplugins.voideconomy.api.VoidEconomyAPI;
import com.voidplugins.voideconomy.command.CurrencyCommand;
import com.voidplugins.voideconomy.command.CurrencyTopCommand;
import com.voidplugins.voideconomy.config.ConfigManager;
import com.voidplugins.voideconomy.currency.Currency;
import com.voidplugins.voideconomy.currency.CurrencyManager;
import com.voidplugins.voideconomy.database.DatabaseManager;
import com.voidplugins.voideconomy.listener.PlayerListener;
import com.voidplugins.voideconomy.placeholder.VoidEconomyExpansion;
import com.voidplugins.voideconomy.store.PlayerStore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.logging.Level;

public final class VoidEconomy extends JavaPlugin {

    private static VoidEconomy instance;

    private ConfigManager configManager;
    private CurrencyManager currencyManager;
    private DatabaseManager databaseManager;
    private PlayerStore playerStore;
    private VoidEconomyAPI api;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);

        if (!databaseManager.connect()) {
            getLogger().severe("Failed to connect to the database! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.currencyManager = new CurrencyManager(this);
        configManager.loadCurrencies();

        this.playerStore = new PlayerStore(this);
        playerStore.startAutoSave();

        VoidEconomyAPI.init(this);
        this.api = VoidEconomyAPI.getInstance();

        registerDynamicCommands();

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VoidEconomyExpansion(this).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        getLogger().info("VoidEconomy v" + getDescription().getVersion() + " enabled with "
                + currencyManager.getCurrencies().size() + " currencies.");
    }

    @Override
    public void onDisable() {
        if (playerStore != null) {
            playerStore.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("VoidEconomy disabled.");
    }

    private void registerDynamicCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            for (Currency currency : currencyManager.getCurrencies()) {
                CurrencyCommand cmd = new CurrencyCommand(this, currency);
                commandMap.register(currency.getCommand(), "voideconomy", cmd);

                CurrencyTopCommand topCmd = new CurrencyTopCommand(this, currency);
                commandMap.register(currency.getCommand() + "top", "voideconomy", topCmd);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Could not register dynamic commands!", e);
        }
    }

    public static VoidEconomy getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerStore getPlayerStore() { return playerStore; }
    public VoidEconomyAPI getAPI() { return api; }
}
