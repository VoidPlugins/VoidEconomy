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
import com.voidplugins.voideconomy.command.PayCommand;
import com.voidplugins.voideconomy.command.VoidEconomyCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;

public final class VoidEconomy extends JavaPlugin {

    private static VoidEconomy instance;

    private ConfigManager configManager;
    private CurrencyManager currencyManager;
    private DatabaseManager databaseManager;
    private PlayerStore playerStore;
    private VoidEconomyAPI api;
    private CommandMap commandMap;

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

        VoidEconomyCommand veCmd = new VoidEconomyCommand(this);
        getCommand("voideconomy").setExecutor(veCmd);
        getCommand("voideconomy").setTabCompleter(veCmd);

        PayCommand payCmd = new PayCommand(this);
        getCommand("pay").setExecutor(payCmd);
        getCommand("pay").setTabCompleter(payCmd);

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

    private void initCommandMap() {
        if (commandMap != null) return;
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            commandMap = (CommandMap) field.get(Bukkit.getServer());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.SEVERE, "Could not access CommandMap!", e);
        }
    }

    private void registerDynamicCommands() {
        initCommandMap();
        if (commandMap == null) return;

        for (Currency currency : currencyManager.getCurrencies()) {
            commandMap.register(currency.getCommand(), "voideconomy", new CurrencyCommand(this, currency));
            commandMap.register(currency.getCommand() + "top", "voideconomy", new CurrencyTopCommand(this, currency));
        }
    }

    private void unregisterDynamicCommands() {
        initCommandMap();
        if (commandMap == null) return;

        try {
            Field knownField = commandMap.getClass().getDeclaredField("knownCommands");
            knownField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, org.bukkit.command.Command> known = (Map<String, org.bukkit.command.Command>) knownField.get(commandMap);

            for (Currency currency : currencyManager.getCurrencies()) {
                known.remove(currency.getCommand());
                known.remove("voideconomy:" + currency.getCommand());
                known.remove(currency.getCommand() + "top");
                known.remove("voideconomy:" + currency.getCommand() + "top");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().log(Level.WARNING, "Could not unregister dynamic commands!", e);
        }
    }

    public void reload() {
        unregisterDynamicCommands();
        currencyManager.clear();
        reloadConfig();
        configManager.loadCurrencies();
        registerDynamicCommands();
        getLogger().info("VoidEconomy reloaded.");
    }

    public static VoidEconomy getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerStore getPlayerStore() { return playerStore; }
    public VoidEconomyAPI getAPI() { return api; }
}
