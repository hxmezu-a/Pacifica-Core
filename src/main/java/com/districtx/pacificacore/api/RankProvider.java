package com.districtx.pacificacore.api;

import java.util.UUID;

/** Optional adapter for a player's primary permissions group. */
public interface RankProvider {
    /** @return whether the backing permissions plugin is available */
    boolean isAvailable();
    /** Gets the player's primary group name. */
    String getPrimaryGroup(UUID playerId);
    /** Gets the configured XP bonus for the requested source. */
    double getXpBonus(UUID playerId, ExperienceSource source);
}