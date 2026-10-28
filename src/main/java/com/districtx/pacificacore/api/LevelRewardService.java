package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.util.List;

/** Public service for configuring, displaying, and granting level rewards. */
public interface LevelRewardService {
    /** Gets configured individual rewards for a level. */
    List<LevelReward> getLevelRewards(int level);
    /** Gets configured every-five-level milestone rewards. */
    List<LevelReward> getFiveLevelRewards(int level);
    /** Gets configured every-hundred-level milestone rewards. */
    List<LevelReward> getHundredLevelRewards(int level);
    /** Gets all rewards in configured execution order. */
    List<LevelReward> getAllRewards(int level);
    /** Grants all unclaimed rewards associated with a level. */
    RewardResult grantLevelRewards(Player player, int level);
    /** Alias for {@link #grantLevelRewards(Player, int)}. */
    RewardResult grantRewards(Player player, int level);
    /** Grants a single unclaimed reward. */
    RewardResult grantReward(Player player, LevelReward reward);
    /** @return whether individual or milestone rewards are configured */
    boolean hasRewards(int level);
    /** @return whether a 5-level or 100-level reward is configured */
    boolean hasMilestoneRewards(int level);
    /** Gets display lore from level-rewards.yml for the requested level. */
    List<String> getDisplayLore(int level);
}