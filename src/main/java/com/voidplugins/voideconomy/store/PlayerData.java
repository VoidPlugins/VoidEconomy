package com.voidplugins.voideconomy.store;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerData {

    private final UUID uuid;
    private volatile String username;
    private final ConcurrentHashMap<String, Double> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public void loadBalances(Map<String, Double> dbBalances) {
        balances.putAll(dbBalances);
        dirty.set(false);
    }

    public double getBalance(String currencyId, double defaultBalance) {
        return balances.getOrDefault(currencyId, defaultBalance);
    }

    public void setBalance(String currencyId, double amount) {
        balances.put(currencyId, amount);
        dirty.set(true);
    }

    public boolean isDirty() { return dirty.get(); }
    public void clearDirty() { dirty.set(false); }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Map<String, Double> getBalances() { return balances; }
}
