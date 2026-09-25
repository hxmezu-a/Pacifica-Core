package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Independent Prestige progression API; Prestige never resets normal level XP. */
public interface PrestigeService {
    /** Gets the player's persistent Prestige count. */
    int getPrestige(UUID playerId);
    default int getPrestige(Player player) { return player == null ? 0 : getPrestige(player.getUniqueId()); }
    /** Checks whether the player reached the configured Prestige unlock level. */
    boolean isUnlocked(UUID playerId);
    default boolean isUnlocked(Player player) { return player != null && isUnlocked(player.getUniqueId()); }
    /** Checks whether the currently configured Prestige mechanics permit an operation. */
    boolean canPrestige(UUID playerId);
    default boolean canPrestige(Player player) { return player != null && canPrestige(player.getUniqueId()); }
    /** Attempts the configured Prestige operation without resetting normal level XP. */
    PrestigeResult prestige(Player player);
}