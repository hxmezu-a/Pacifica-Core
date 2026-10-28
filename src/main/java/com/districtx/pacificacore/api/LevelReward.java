package com.districtx.pacificacore.api;

/** A configured or plugin-provided reward for a level milestone. */
public interface LevelReward {
    /** Gets the persistent unique ID used for duplicate-claim protection. */
    String getId();
    /** Gets the reward type. */
    LevelRewardType getType();

    /** Executes this reward and returns whether its operation completed successfully. */
    boolean execute(org.bukkit.entity.Player player, LevelRewardContext context);
}