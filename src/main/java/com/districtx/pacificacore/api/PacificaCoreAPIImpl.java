package com.districtx.pacificacore.api;

public final class PacificaCoreAPIImpl implements PacificaCoreAPI {
    private final DiamondCurrencyService diamonds;
    private final EconomyService economy;
    private final TransactionService transactions;

    public PacificaCoreAPIImpl(DiamondCurrencyService diamonds, EconomyService economy,
                               TransactionService transactions) {
        this.diamonds = diamonds;
        this.economy = economy;
        this.transactions = transactions;
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
}