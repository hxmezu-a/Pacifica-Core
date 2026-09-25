package com.districtx.pacificacore.placeholder;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.ExperienceSource;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

/** PlaceholderAPI expansion for permanent Pacifica Player Level values. */
public final class PlayerLevelPlaceholderExpansion extends PlaceholderExpansion {
    private final PacificaCore plugin;

    /**
     * Creates the expansion.
     *
     * @param plugin owning Pacifica-Core plugin
     */
    public PlayerLevelPlaceholderExpansion(PacificaCore plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "pacifica"; }
    @Override public String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || !plugin.getLevelConfig().getBoolean("leveling.integrations.placeholder-api", true)) {
            return null;
        }
        if (player == null) return defaultValue(params);
        PlayerLevelService levels = plugin.getAPI().getPlayerLevelService();
        java.util.UUID playerId = player.getUniqueId();
        double experience = levels.getExperience(playerId);
        int level = levels.getLevel(playerId);
        double progress = levels.getProgressToNextLevel(playerId);
        String placeholder = params.toLowerCase(Locale.ROOT);
        return switch (placeholder) {
            case "level", "level_current" -> String.valueOf(level);
            case "level_max" -> plugin.getLevelConfig().getString("placeholders.infinite-level-max", "∞");
            case "exp", "experience" -> format(experience);
            case "exp_to_next_level" -> format(levels.getExperienceToNextLevel(playerId));
            case "level_progress" -> String.format(Locale.US, "%.2f", progress);
            case "level_progress_percent" -> String.valueOf(Math.round(progress * 100.0));
            case "exp_required" -> format(levels.getProgression().getExperienceRequiredForNextLevel(level));
            case "daily_xp" -> format(plugin.getAPI().getDailyExperienceService().getExperienceAmount(playerId));
            case "daily_xp_price" -> format(plugin.getAPI().getDailyExperienceService().getPrice(playerId));
            case "daily_xp_cooldown" -> formatDuration(
                    plugin.getAPI().getDailyExperienceService().getRemainingCooldown(playerId));
            case "prestige" -> String.valueOf(plugin.getAPI().getPrestigeService().getPrestige(playerId));
            case "rank_xp_bonus" -> format(plugin.getAPI().getRankExperienceBonusService()
                    .getBonusPercentage(playerId, ExperienceSource.PLAYER_KILL));
            default -> null;
        };
    }

    private String defaultValue(String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "level", "level_current" -> "1";
            case "level_max" -> plugin.getLevelConfig().getString("placeholders.infinite-level-max", "∞");
            case "exp", "experience", "exp_to_next_level", "level_progress", "level_progress_percent",
                    "exp_required", "daily_xp", "daily_xp_price", "daily_xp_cooldown", "prestige",
                    "rank_xp_bonus" -> "0";
            default -> null;
        };
    }

    private String format(double value) {
        if (Double.isInfinite(value)) return "∞";
        if (Double.isNaN(value)) return "0";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) return "0m";
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        String path = days > 0 ? "daily-xp.cooldown-format.days"
                : hours > 0 ? "daily-xp.cooldown-format.hours" : "daily-xp.cooldown-format.minutes";
        String fallback = days > 0 ? "%days%d %hours%h" : hours > 0 ? "%hours%h %minutes%m" : "%minutes%m";
        return plugin.getLevelConfig().getString(path, fallback)
                .replace("%days%", String.valueOf(days))
                .replace("%hours%", String.valueOf(hours))
                .replace("%minutes%", String.valueOf(Math.max(1, minutes)));
    }
}