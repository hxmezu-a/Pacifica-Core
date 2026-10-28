package com.districtx.pacificacore.economy;

import com.districtx.pacificacore.api.BankingService;
import com.districtx.pacificacore.bank.BankingGuiManager;
import com.districtx.pacificacore.storage.BankingRepository;
import com.districtx.pacificacore.storage.CurrencyStorage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public final class BankingServiceImpl implements BankingService, AutoCloseable {
    private final JavaPlugin plugin;
    private final CurrencyStorage currencyStorage;
    private final BankingRepository repository;
    private final BankingGuiManager guiManager;
    private final ExecutorService operations = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Pacifica-Core-Banking");
        thread.setDaemon(true);
        return thread;
    });

    public BankingServiceImpl(JavaPlugin plugin, CurrencyStorage currencyStorage,
                              BankingRepository repository, BankingGuiManager guiManager) {
        this.plugin = plugin;
        this.currencyStorage = currencyStorage;
        this.repository = repository;
        this.guiManager = guiManager;
    }

    @Override public void openBankingMenu(Player player) { guiManager.openMainMenu(player); }
    @Override public void openDepositMenu(Player player) { guiManager.openAmountMenu(player, BankingGuiManager.Flow.DEPOSIT); }
    @Override public void openWithdrawMenu(Player player) { guiManager.openAmountMenu(player, BankingGuiManager.Flow.WITHDRAW); }
    @Override public void openTransferMenu(Player player) { guiManager.openAmountMenu(player, BankingGuiManager.Flow.TRANSFER); }

    @Override
    public CompletableFuture<BigDecimal> getBankBalance(UUID playerId) {
        if (playerId == null) return CompletableFuture.failedFuture(new IllegalArgumentException("playerId cannot be null"));
        try {
            return CompletableFuture.supplyAsync(() -> repository.getBalance(playerId), operations);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID playerId, BigDecimal amount) {
        if (!valid(playerId, amount)) return CompletableFuture.completedFuture(false);
        return submit(() -> currencyStorage.depositToBank(playerId, amount, repository), "deposit");
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerId, BigDecimal amount) {
        if (!valid(playerId, amount)) return CompletableFuture.completedFuture(false);
        return submit(() -> currencyStorage.withdrawFromBank(playerId, amount, repository), "withdraw");
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID senderId, UUID recipientId, BigDecimal amount) {
        if (!valid(senderId, amount) || recipientId == null || senderId.equals(recipientId)) {
            return CompletableFuture.completedFuture(false);
        }
        return submit(() -> currencyStorage.transferBank(senderId, recipientId, amount, repository), "transfer");
    }

    private CompletableFuture<Boolean> submit(Supplier<Boolean> transaction, String name) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return transaction.get();
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Bank " + name + " failed: " + exception.getMessage());
                    return false;
                }
            }, operations);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean valid(UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null || amount.signum() <= 0) return false;
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() <= 8 && normalized.precision() - normalized.scale() <= 38;
    }

    @Override
    public void close() {
        operations.shutdown();
    }
}