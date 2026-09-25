package com.districtx.pacificacore.api;

public final class PacificaCoreAPIImpl implements PacificaCoreAPI {
    private final DiamondCurrencyService diamonds;
    private final EconomyService economy;
    private final TransactionService transactions;
    private final PlayerLevelService playerLevels;

    public PacificaCoreAPIImpl(DiamondCurrencyService diamonds, EconomyService economy,
                               TransactionService transactions, PlayerLevelService playerLevels) {
        this.diamonds = diamonds;
        this.economy = economy;
        this.transactions = transactions;
        this.playerLevels = playerLevels;
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
}