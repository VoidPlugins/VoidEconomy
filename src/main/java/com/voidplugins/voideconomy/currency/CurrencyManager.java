package com.voidplugins.voideconomy.currency;

import com.voidplugins.voideconomy.VoidEconomy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyManager {

    private final VoidEconomy plugin;
    private final Map<String, Currency> byId = new LinkedHashMap<>();
    private final Map<String, Currency> byCommand = new ConcurrentHashMap<>();

    // currencyId -> cached top list, refreshed periodically
    private final Map<String, List<TopEntry>> topCache = new ConcurrentHashMap<>();

    public CurrencyManager(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    public void register(Currency currency) {
        byId.put(currency.getId(), currency);
        byCommand.put(currency.getCommand().toLowerCase(), currency);
    }

    public Currency getById(String id) {
        return byId.get(id.toLowerCase());
    }

    public Currency getByCommand(String command) {
        return byCommand.get(command.toLowerCase());
    }

    public Collection<Currency> getCurrencies() {
        return byId.values();
    }

    public List<TopEntry> getTopCache(String currencyId) {
        return topCache.getOrDefault(currencyId, Collections.emptyList());
    }

    public void updateTopCache(String currencyId, List<TopEntry> entries) {
        topCache.put(currencyId, entries);
    }

    public record TopEntry(int rank, String playerName, double balance) {}
}
