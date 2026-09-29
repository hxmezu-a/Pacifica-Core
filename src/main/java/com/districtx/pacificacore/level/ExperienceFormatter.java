package com.districtx.pacificacore.level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

public final class ExperienceFormatter {
    private ExperienceFormatter() {
    }

    /** Formats reward experience without insignificant decimal zeros. */
    public static String formatReward(double experience) {
        if (!Double.isFinite(experience)) return "0";
        return BigDecimal.valueOf(experience).stripTrailingZeros().toPlainString();
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