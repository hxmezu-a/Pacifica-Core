package com.districtx.pacificacore.placeholder;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.PlayerLevelService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
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
        if (params == null || !plugin.getConfig().getBoolean("player-level.integrations.placeholder-api", true)) {
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
            case "level_max" -> String.valueOf(levels.getMaximumLevel());
            case "exp", "experience" -> format(experience);
            case "exp_to_next_level" -> format(levels.getExperienceToNextLevel(playerId));
            case "level_progress" -> String.format(Locale.US, "%.2f", progress);
            case "level_progress_percent" -> String.valueOf(Math.round(progress * 100.0));
            case "exp_required" -> level >= levels.getMaximumLevel() ? "0.0"
                    : format(levels.getExperienceRequiredForLevel(level + 1));
            default -> null;
        };
    }

    private String defaultValue(String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "level", "level_current" -> "1";
            case "level_max" -> "100";
            case "exp", "experience", "exp_to_next_level", "level_progress", "level_progress_percent",
                    "exp_required" -> "0";
            default -> null;
        };
    }

    private String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}