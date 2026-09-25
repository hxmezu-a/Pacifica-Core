package com.districtx.pacificacore.api;

public final class PacificaCoreAPIImpl implements PacificaCoreAPI {
    private final DiamondCurrencyService diamonds;
    private final EconomyService economy;
    private final TransactionService transactions;
    private final PlayerLevelService playerLevels;
    private final LevelRewardService levelRewards;
    private final DailyExperienceService dailyExperience;
    private final PrestigeService prestige;
    private final RankExperienceBonusService rankBonuses;
    private final LevelMenuService levelMenus;

    public PacificaCoreAPIImpl(DiamondCurrencyService diamonds, EconomyService economy,
                               TransactionService transactions, PlayerLevelService playerLevels,
                               LevelRewardService levelRewards, DailyExperienceService dailyExperience,
                               PrestigeService prestige, RankExperienceBonusService rankBonuses,
                               LevelMenuService levelMenus) {
        this.diamonds = diamonds;
        this.economy = economy;
        this.transactions = transactions;
        this.playerLevels = playerLevels;
        this.levelRewards = levelRewards;
        this.dailyExperience = dailyExperience;
        this.prestige = prestige;
        this.rankBonuses = rankBonuses;
        this.levelMenus = levelMenus;
    }

    @Override
    public DiamondCurrencyService getDiamondCurrencyService() {
        return diamonds;
    }

    @Override
    public EconomyService getEconomyService() {
        return economy;
    }

    @Override
    public TransactionService getTransactionService() {
        return transactions;
    }

    @Override
    public PlayerLevelService getPlayerLevelService() {
        return playerLevels;
    }

    @Override
    public LevelRewardService getLevelRewardService() {
        return levelRewards;
    }

    @Override public DailyExperienceService getDailyExperienceService() { return dailyExperience; }
    @Override public PrestigeService getPrestigeService() { return prestige; }
    @Override public RankExperienceBonusService getRankExperienceBonusService() { return rankBonuses; }
    @Override public LevelMenuService getLevelMenuService() { return levelMenus; }
}