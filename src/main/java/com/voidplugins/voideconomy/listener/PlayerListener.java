package com.voidplugins.voideconomy.listener;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.store.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final VoidEconomy plugin;

    public PlayerListener(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    /**
     * Load player data early in the login flow so it's ready when they join.
     * AsyncPlayerPreLoginEvent runs on an async thread, which is perfect for DB I/O.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        // Sync wait on the CompletableFuture — we are already on an async thread
        try {
            plugin.getPlayerStore().loadPlayer(uuid, name).get();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load data for " + name + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getPlayerStore().savePlayer(uuid);
    }
}
