package com.districtx.pacificacore.level;

import com.districtx.pacificacore.PacificaCore;

import java.util.Map;

public final class LevelDisplayFormatter {
    private LevelDisplayFormatter() { }

    public static String format(PacificaCore plugin, int level) {
        return colorPrefix(plugin, level) + level;
    }

    public static String colorPrefix(PacificaCore plugin, int level) {
        if (level >= 101) return "&4&l";
        for (Map<?, ?> range : plugin.getLevelConfig().getMapList("level-colors")) {
            Object minimum = range.get("min-level");
            Object maximum = range.get("max-level");
            if (minimum instanceof Number min && maximum instanceof Number max
                    && level >= min.intValue() && level <= max.intValue()) {
                Object color = range.get("color");
                return color == null ? "&7" : String.valueOf(color);
            }
        }
        return "&7";
    }
}