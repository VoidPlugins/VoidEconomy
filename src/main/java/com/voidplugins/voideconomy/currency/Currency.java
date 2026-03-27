package com.voidplugins.voideconomy.currency;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

public class Currency {

    private final String id;
    private final String displayName;
    private final String symbol;
    private final String command;
    private final double defaultBalance;
    private final double maxBalance;
    private final int decimals;
    private final Map<String, String> messages;
    private final DecimalFormat formatter;

    public Currency(String id, String displayName, String symbol, String command,
                    double defaultBalance, double maxBalance, int decimals,
                    Map<String, String> messages) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.command = command;
        this.defaultBalance = defaultBalance;
        this.maxBalance = maxBalance;
        this.decimals = decimals;
        this.messages = messages;

        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append(".");
            pattern.append("0".repeat(decimals));
        }
        this.formatter = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.US));
    }

    public String formatAmount(double amount) {
        return formatter.format(amount);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "&cMissing message: " + key);
    }

    public boolean hasMessage(String key) {
        return messages.containsKey(key);
    }

    public boolean hasMaxBalance() { return maxBalance > 0; }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getSymbol() { return symbol; }
    public String getCommand() { return command; }
    public double getDefaultBalance() { return defaultBalance; }
    public double getMaxBalance() { return maxBalance; }
    public int getDecimals() { return decimals; }
}
