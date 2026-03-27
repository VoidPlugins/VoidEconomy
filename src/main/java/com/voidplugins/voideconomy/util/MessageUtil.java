package com.voidplugins.voideconomy.util;

import net.md_5.bungee.api.ChatColor;

public final class MessageUtil {

    private MessageUtil() {}

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
}
