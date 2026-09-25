package com.districtx.pacificacore.api;

import java.util.UUID;

/** Immutable summary of one Pacifica experience gain attempt. */
public final class ExperienceGainResult {
    private final UUID playerId;
    private final double previousExperience;
    private final double experienceAdded;
    private final double newExperience;
    private final int previousLevel;
    private final int newLevel;
    private final ExperienceSource source;

    /**
     * Creates an experience gain result.
     *
     * @param playerId player UUID
     * @param previousExperience experience before the operation
     * @param experienceAdded experience actually applied
     * @param newExperience experience after the operation
     * @param previousLevel level before the operation
     * @param newLevel level after the operation
     * @param source source of the attempted gain
     */
    public ExperienceGainResult(UUID playerId, double previousExperience, double experienceAdded,
                                double newExperience, int previousLevel, int newLevel,
                                ExperienceSource source) {
        this.playerId = playerId;
        this.previousExperience = previousExperience;
        this.experienceAdded = experienceAdded;
        this.newExperience = newExperience;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.source = source;
    }

    /** @return UUID of the player whose experience was changed */
    public UUID getPlayerId() { return playerId; }

    /** @return experience before the operation */
    public double getPreviousExperience() { return previousExperience; }

    /** @return experience actually applied; zero when rejected or cancelled */
    public double getExperienceAdded() { return experienceAdded; }

    /** @return experience after the operation */
    public double getNewExperience() { return newExperience; }

    /** @return level before the operation */
    public int getPreviousLevel() { return previousLevel; }

    /** @return level after the operation */
    public int getNewLevel() { return newLevel; }

    /** @return whether the operation increased the player's calculated level */
    public boolean isLevelUp() { return newLevel > previousLevel; }

    /** @return source associated with the attempted gain */
    public ExperienceSource getSource() { return source; }
}