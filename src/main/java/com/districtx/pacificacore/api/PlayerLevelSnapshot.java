package com.districtx.pacificacore.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable, exact-value view of a player's Pacifica progression. */
public record PlayerLevelSnapshot(
        UUID playerId,
        int level,
        BigDecimal totalExperience,
        BigDecimal experienceIntoCurrentLevel,
        BigDecimal experienceRequiredForNextLevel,
        BigDecimal experienceToNextLevel,
        double progressToNextLevel,
        boolean canAdvanceToNextLevel
) { }