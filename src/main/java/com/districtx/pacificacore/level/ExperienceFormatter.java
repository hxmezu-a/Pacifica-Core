package com.districtx.pacificacore.level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ExperienceFormatter {
    private ExperienceFormatter() {
    }

    /** Formats player-facing XP with one decimal place without changing stored precision. */
    public static String formatXp(double experience) {
        return formatOneDecimal(experience);
    }

    /** Formats player-facing prices with the same precision as XP. */
    public static String formatPrice(double price) {
        return formatOneDecimal(price);
    }

    /** Formats a player-facing reward amount. */
    public static String formatReward(double experience) {
        String formatted = formatXp(experience);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    public static String formatOneDecimal(double value) {
        if (Double.isNaN(value)) return "0.0";
        if (Double.isInfinite(value)) return value < 0.0 ? "-∞" : "∞";
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /** Resolves installed PlaceholderAPI placeholders when that plugin is available. */
    public static String applyPlaceholders(Player player, String message) {
        if (player == null) return message;
        org.bukkit.plugin.Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) return message;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true,
                    placeholderApi.getClass().getClassLoader());
            Object result = api.getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, message);
            return result instanceof String formatted ? formatted : message;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return message;
        }
    }
}