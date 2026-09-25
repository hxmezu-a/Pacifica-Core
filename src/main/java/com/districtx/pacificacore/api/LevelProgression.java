package com.districtx.pacificacore.api;

/** Calculates unlimited levels and progress from permanent Pacifica experience. */
public interface LevelProgression {
    /**
     * Calculates a level for an experience amount.
     *
     * @param experience permanent Pacifica experience
     * @return calculated level
     */
    int getLevel(double experience);

    /**
     * Gets the cumulative experience threshold for a level.
     *
     * @param level requested level
     * @return cumulative experience required to reach the requested level
     */
    double getExperienceRequiredForLevel(int level);

    /** @return cumulative experience threshold for the requested level */
    default double getTotalExperienceRequiredForLevel(int level) {
        return getExperienceRequiredForLevel(level);
    }

    /** @return experience gained since the start of the current level */
    default double getExperienceIntoCurrentLevel(double totalExperience) {
        if (!Double.isFinite(totalExperience)) return 0.0;
        int level = getLevel(totalExperience);
        return Math.max(0.0, Math.max(0.0, totalExperience) - getExperienceRequiredForLevel(level));
    }

    /** @return XP requirement for the transition from {@code level} to {@code level + 1} */
    default double getExperienceRequiredForNextLevel(int level) {
        return getExperienceRequiredForLevel(level + 1) - getExperienceRequiredForLevel(level);
    }

    /**
     * Gets the remaining experience to the next level.
     *
     * @param experience current permanent Pacifica experience
     * @return remaining experience, or zero at the maximum level
     */
    double getExperienceToNextLevel(double experience);

    /** @return progress to the next level as a fraction from zero to one */
    default double getProgress(double totalExperience) {
        return getProgressToNextLevel(totalExperience);
    }

    /** @return progress to the next level as a value suitable for the client EXP bar */
    default float getExpBarProgress(double totalExperience) {
        return (float) Math.max(0.0, Math.min(1.0, getProgressToNextLevel(totalExperience)));
    }

    /**
     * Gets progress to the next level as a fraction from zero to one.
     *
     * @param experience current permanent Pacifica experience
     * @return progress fraction; maximum-level players have complete progress
     */
    double getProgressToNextLevel(double experience);

    /** @return configured minimum level */
    int getMinimumLevel();

    /** @return configured maximum level, or {@link Integer#MAX_VALUE} for unlimited progression */
    int getMaximumLevel();
}