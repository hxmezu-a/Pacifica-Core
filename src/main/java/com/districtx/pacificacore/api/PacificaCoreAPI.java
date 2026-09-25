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
}