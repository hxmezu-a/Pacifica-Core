package com.districtx.pacificacore.api;

public interface PacificaCoreAPI {
    DiamondCurrencyService getDiamondCurrencyService();

    EconomyService getEconomyService();

    TransactionService getTransactionService();

    /**
     * Gets the service for permanent Pacifica Player Level progression.
     *
     * @return the Player Level service
     */
    PlayerLevelService getPlayerLevelService();

    /** Gets the service for configurable level and milestone rewards. */
    LevelRewardService getLevelRewardService();

    /** Gets the persistent Daily XP purchase service. */
    DailyExperienceService getDailyExperienceService();

    /** Gets the independent Prestige service. */
    PrestigeService getPrestigeService();

    /** Gets optional LuckPerms-backed XP bonus calculations. */
    RankExperienceBonusService getRankExperienceBonusService();

    /** Gets the public Level and Prestige menu controller. */
    LevelMenuService getLevelMenuService();
}