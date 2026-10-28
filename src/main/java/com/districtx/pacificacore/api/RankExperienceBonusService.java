package com.districtx.pacificacore.api;

import java.util.UUID;

/** Public API for optional rank-based Player Level experience bonuses. */
public interface RankExperienceBonusService {
    /** @return whether the optional rank provider is available */
    boolean isAvailable();
    /** Gets the player's LuckPerms primary group, or the default rank when unavailable. */
    String getPrimaryRank(UUID playerId);
    /** Gets the configured bonus value for an XP source. */
    double getBonusPercentage(UUID playerId, ExperienceSource source);
    /** Applies the configured percentage or flat rank bonus to base XP. */
    double applyBonus(UUID playerId, ExperienceSource source, double baseExperience);
}