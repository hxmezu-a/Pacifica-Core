package com.districtx.pacificacore.level;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.PrestigeResult;
import com.districtx.pacificacore.api.PrestigeService;
import com.districtx.pacificacore.storage.PrestigeRepository;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Persistent Prestige data and unlock checks; actual prestige mechanics remain opt-in for future configuration. */
public final class PrestigeManager implements PrestigeService {
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final PrestigeRepository repository;

    public PrestigeManager(PacificaCore plugin, PlayerLevelService levels, PrestigeRepository repository) {
        this.plugin = plugin;
        this.levels = levels;
        this.repository = repository;
    }

    @Override public int getPrestige(UUID playerId) { return playerId == null ? 0 : repository.getPrestige(playerId); }

    @Override
    public boolean isUnlocked(UUID playerId) {
        return playerId != null && plugin.getLevelConfig().getBoolean("prestige.enabled", true)
                && levels.getLevel(playerId) >= plugin.getLevelConfig().getInt("prestige.unlock-level", 100);
    }

    @Override
    public boolean canPrestige(UUID playerId) {
        return isUnlocked(playerId) && plugin.getLevelConfig().getBoolean("prestige.mechanics-enabled", false);
    }

    @Override
    public PrestigeResult prestige(Player player) {
        if (player == null) return new PrestigeResult(false, 0, "player-unavailable");
        int current = getPrestige(player.getUniqueId());
        if (!isUnlocked(player.getUniqueId())) return new PrestigeResult(false, current, "locked");
        return new PrestigeResult(false, current, "prestige-mechanics-not-configured");
    }
}