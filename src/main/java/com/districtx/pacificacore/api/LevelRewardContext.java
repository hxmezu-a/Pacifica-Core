package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

/** Immutable progression details supplied to a level reward. */
public final class LevelRewardContext {
    private final Player player;
    private final int level;
    private final int previousLevel;
    private final double totalExperience;

    public LevelRewardContext(Player player, int level, int previousLevel, double totalExperience) {
        this.player = player;
        this.level = level;
        this.previousLevel = previousLevel;
        this.totalExperience = totalExperience;
    }

    /** @return player receiving this reward */
    public Player getPlayer() { return player; }
    /** @return level being rewarded */
    public int getLevel() { return level; }
    /** @return level before this transition */
    public int getPreviousLevel() { return previousLevel; }
    /** @return total experience after the transition */
    public double getTotalExperience() { return totalExperience; }
}