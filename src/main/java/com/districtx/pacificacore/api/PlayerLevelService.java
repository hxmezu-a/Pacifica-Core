package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/** Public API for permanent Pacifica Player Level progression. */
public interface PlayerLevelService {
    /**
     * Gets a player's permanent Pacifica experience. Unknown players have zero experience.
     *
     * @param playerId player UUID
     * @return Pacifica experience
     */
    double getExperience(UUID playerId);

    /**
     * Gets a player's calculated Pacifica level. Unknown players are at the minimum level.
     *
     * @param playerId player UUID
     * @return calculated level
     */
    int getLevel(UUID playerId);

    /**
     * Gets the remaining experience needed for the next level.
     *
     * @param playerId player UUID
     * @return remaining experience, or zero at the maximum level
     */
    double getExperienceToNextLevel(UUID playerId);

    /**
     * Gets the cumulative experience threshold for a level.
     *
     * @param level requested level
     * @return cumulative threshold for the requested level
     */
    double getExperienceRequiredForLevel(int level);

    /** Gets the XP cost of progressing from a level to its next level. */
    default double getExperienceRequiredForNextLevel(int level) {
        return getProgression().getExperienceRequiredForNextLevel(level);
    }

    /** Gets the cumulative XP threshold required to reach a level. */
    default double getTotalExperienceRequiredForLevel(int level) {
        return getProgression().getTotalExperienceRequiredForLevel(level);
    }

    /** Gets XP earned since the current level began. */
    default double getExperienceIntoCurrentLevel(UUID playerId) {
        return getProgression().getExperienceIntoCurrentLevel(getExperience(playerId));
    }

    /**
     * Gets progress to the next level as a fraction from zero to one.
     *
     * @param playerId player UUID
     * @return progress fraction; maximum-level players have complete progress
     */
    double getProgressToNextLevel(UUID playerId);

    /** Gets progress to the next level as a fraction from zero to one. */
    default double getProgress(UUID playerId) {
        return getProgressToNextLevel(playerId);
    }

    /** @return configured minimum level */
    int getMinimumLevel();

    /** @return configured maximum level */
    int getMaximumLevel();

    /** Gets the authoritative progression calculator used by this service. */
    LevelProgression getProgression();

    /**
     * Adds experience using the API source.
     *
     * @param playerId player UUID
     * @param amount positive experience amount
     * @return whether a positive amount was applied
     */
    boolean addExperience(UUID playerId, double amount);

    /**
     * Adds experience to a player using the API source.
     *
     * @param player player to reward
     * @param amount positive experience amount
     * @return whether a positive amount was applied
     */
    boolean addExperience(Player player, double amount);

    /**
     * Adds experience and returns the resulting progression snapshot.
     *
     * @param playerId player UUID
     * @param amount positive experience amount
     * @param source reason for the gain
     * @return result with previous and resulting experience and level
     */
    ExperienceGainResult addExperience(UUID playerId, double amount, ExperienceSource source);

    /**
     * Adds experience to a player and returns the resulting progression snapshot.
     *
     * @param player player to reward
     * @param amount positive experience amount
     * @param source reason for the gain
     * @return result with previous and resulting experience and level
     */
    ExperienceGainResult addExperience(Player player, double amount, ExperienceSource source);

    /**
     * Removes experience without allowing the total to fall below zero.
     *
     * @param playerId player UUID
     * @param amount non-negative amount to remove
     * @return whether the update was persisted
     */
    boolean removeExperience(UUID playerId, double amount);

    /**
     * Sets a player's permanent experience to a non-negative amount.
     *
     * @param playerId player UUID
     * @param amount new experience total
     * @return whether the update was persisted
     */
    boolean setExperience(UUID playerId, double amount);

    /**
     * Sets a player's level by assigning that level's cumulative experience threshold.
     *
     * @param playerId player UUID
     * @param level target level
     * @return whether the level was valid and the update was persisted
     */
    boolean setLevel(UUID playerId, int level);

    /**
     * Checks whether a player has at least the requested experience.
     *
     * @param playerId player UUID
     * @param amount non-negative amount to check
     * @return true when the stored total meets or exceeds the amount
     */
    boolean hasExperience(UUID playerId, double amount);
}