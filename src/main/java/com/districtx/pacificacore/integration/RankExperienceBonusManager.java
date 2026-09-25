package com.districtx.pacificacore.integration;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.RankExperienceBonusService;
import com.districtx.pacificacore.api.RankProvider;

import java.util.Locale;
import java.util.UUID;

/** Calculates configured rank XP modifiers, falling back to the default rank when LuckPerms is absent. */
public final class RankExperienceBonusManager implements RankExperienceBonusService {
    private final PacificaCore plugin;
    private final RankProvider provider;

    public RankExperienceBonusManager(PacificaCore plugin, RankProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    @Override public boolean isAvailable() { return provider.isAvailable(); }

    @Override
    public String getPrimaryRank(UUID playerId) {
        return provider.isAvailable() ? provider.getPrimaryGroup(playerId) : "default";
    }

    @Override
    public double getBonusPercentage(UUID playerId, ExperienceSource source) {
        if (!plugin.getLevelConfig().getBoolean("rank-bonuses.enabled", true)) return 0.0;
        String sourceKey = sourceKey(source);
        String defaultPath = "rank-bonuses.default." + sourceKey;
        double defaultBonus = plugin.getLevelConfig().getDouble(defaultPath, 0.0);
        if (!provider.isAvailable()) return defaultBonus;
        return provider.getXpBonus(playerId, source);
    }

    @Override
    public double applyBonus(UUID playerId, ExperienceSource source, double baseExperience) {
        if (!Double.isFinite(baseExperience) || baseExperience <= 0.0) return 0.0;
        if (source == ExperienceSource.DAILY_XP
                && !plugin.getLevelConfig().getBoolean("daily-xp.apply-rank-bonus", false)) {
            return baseExperience;
        }
        double bonus = getBonusPercentage(playerId, source);
        String mode = plugin.getLevelConfig().getString("rank-bonuses.mode", "PERCENTAGE");
        double adjusted = "FLAT".equalsIgnoreCase(mode)
                ? baseExperience + bonus : baseExperience * (1.0 + bonus / 100.0);
        return Double.isFinite(adjusted) ? Math.max(0.0, adjusted) : baseExperience;
    }

    private String sourceKey(ExperienceSource source) {
        if (source == null) return "other";
        return switch (source) {
            case PLAYER_KILL -> "kill";
            case PVP_DEATH -> "death";
            case LOOT -> "loot";
            case DAILY_XP -> "daily-xp";
            default -> "other";
        };
    }
}