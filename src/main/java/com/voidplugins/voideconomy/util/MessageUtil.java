package com.voidplugins.voideconomy.util;

import net.md_5.bungee.api.ChatColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MessageUtil {

    private MessageUtil() {}

    private static final DecimalFormat COMPACT_FORMAT =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    // Ordered largest → smallest. Check longer suffixes before shorter ones (e.g. "qt" before "q").
    private static final Object[][] TIERS = {
        {1e63, "Vi"},   // vigintillion
        {1e60, "Nd"},   // novemdecillion
        {1e57, "Od"},   // octodecillion
        {1e54, "Sd"},   // septendecillion
        {1e51, "Sxd"},  // sexdecillion
        {1e48, "Qid"},  // quindecillion
        {1e45, "Qtd"},  // quattuordecillion
        {1e42, "Trd"},  // tredecillion
        {1e39, "Dod"},  // duodecillion
        {1e36, "Ud"},   // undecillion
        {1e33, "Dc"},   // decillion
        {1e30, "No"},   // nonillion
        {1e27, "Oc"},   // octillion
        {1e24, "Sp"},   // septillion
        {1e21, "Sx"},   // sextillion
        {1e18, "Qt"},   // quintillion
        {1e15, "Q"},    // quadrillion
        {1e12, "T"},    // trillion
        {1e9,  "B"},    // billion
        {1e6,  "M"},    // million
        {1e3,  "K"},    // thousand
    };

    // Same order as TIERS, used for parseAmount. Longer suffixes must come first.
    private static final Object[][] PARSE_SUFFIXES = {
        {"vi",  1e63}, {"nd",  1e60}, {"od",  1e57}, {"sd",  1e54},
        {"sxd", 1e51}, {"qid", 1e48}, {"qtd", 1e45}, {"trd", 1e42},
        {"dod", 1e39}, {"ud",  1e36}, {"dc",  1e33}, {"no",  1e30},
        {"oc",  1e27}, {"sp",  1e24}, {"sx",  1e21}, {"qt",  1e18},
        {"q",   1e15}, {"t",   1e12}, {"b",   1e9},  {"m",   1e6},
        {"k",   1e3},
    };

    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String replace(String message, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Returns a compact, human-readable representation of a large number.
     * Examples: 1500 → "1.5K", 2300000 → "2.3M", 1000000000000L → "1T"
     */
    public static String formatCompact(double amount) {
        if (amount < 0) return "-" + formatCompact(-amount);
        for (Object[] tier : TIERS) {
            double threshold = (double) tier[0];
            if (amount >= threshold) {
                return COMPACT_FORMAT.format(amount / threshold) + tier[1];
            }
        }
        return COMPACT_FORMAT.format(amount);
    }

    /**
     * Parses a human-friendly amount string into a double.
     * Supports suffixes: k, m, b, t, q, qt, sx, sp, oc, no, dc, ... (case-insensitive).
     * Also strips commas and underscores used as thousand separators.
     * Examples: "1k" → 1000, "1.5m" → 1500000, "1qt" → 1e18, "1,000" → 1000
     *
     * @return the parsed value, or -1 if the input is invalid or negative
     */
    public static double parseAmount(String input) {
        if (input == null || input.isEmpty()) return -1;

        String cleaned = input.replace(",", "").replace("_", "").toLowerCase().trim();

        double multiplier = 1;
        for (Object[] entry : PARSE_SUFFIXES) {
            String suffix = (String) entry[0];
            if (cleaned.endsWith(suffix)) {
                multiplier = (double) entry[1];
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length());
                break;
            }
        }

        try {
            double value = Double.parseDouble(cleaned) * multiplier;
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
