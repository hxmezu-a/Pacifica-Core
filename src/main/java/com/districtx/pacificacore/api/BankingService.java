package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Public API for opening banking menus and executing validated server-balance transactions. */
public interface BankingService {
    /** Opens the player's private banking overview. */
    void openBankingMenu(Player player);

    /** Opens the deposit amount menu for the player. */
    void openDepositMenu(Player player);

    /** Opens the withdrawal amount menu for the player. */
    void openWithdrawMenu(Player player);

    /** Opens the transfer amount menu for the player. */
    void openTransferMenu(Player player);

    /** Returns the persistent bank balance, completing exceptionally if storage fails. */
    CompletableFuture<BigDecimal> getBankBalance(UUID playerId);

    /** Atomically moves a positive, supported amount from server balance into the bank. */
    CompletableFuture<Boolean> deposit(UUID playerId, BigDecimal amount);

    /** Atomically moves a positive, supported amount from the bank into server balance. */
    CompletableFuture<Boolean> withdraw(UUID playerId, BigDecimal amount);

    /** Atomically transfers a positive, supported bank amount to a different player. */
    CompletableFuture<Boolean> transfer(UUID senderId, UUID recipientId, BigDecimal amount);
}