package com.districtx.pacificacore.api;

/** Calculates levels and progress from permanent Pacifica experience. */
public interface LevelProgression {
    /**
     * Calculates a level for an experience amount, clamped to the configured range.
     *
     * @param experience permanent Pacifica experience
     * @return calculated level
     */
    int getLevel(double experience);

    /**
     * Gets the cumulative experience threshold for a level.
     *
     * @param level requested level
     * @return cumulative experience required to reach the clamped level
     */
    double getExperienceRequiredForLevel(int level);

    /**
     * Gets the remaining experience to the next level.
     *
     * @param experience current permanent Pacifica experience
     * @return remaining experience, or zero at the maximum level
     */
    double getExperienceToNextLevel(double experience);

    /**
     * Gets progress to the next level as a fraction from zero to one.
     *
     * @param experience current permanent Pacifica experience
     * @return progress fraction; maximum-level players have complete progress
     */
    double getProgressToNextLevel(double experience);

    /** @return configured minimum level */
    int getMinimumLevel();

    /** @return configured maximum level */
    int getMaximumLevel();
}