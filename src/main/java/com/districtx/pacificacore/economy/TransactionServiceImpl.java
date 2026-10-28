package com.districtx.pacificacore.economy;

import com.districtx.pacificacore.api.TransactionService;
import com.districtx.pacificacore.storage.CurrencyStorage;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class TransactionServiceImpl implements TransactionService {
    private final CurrencyStorage storage;

    public TransactionServiceImpl(CurrencyStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean transferDiamond(UUID from, UUID to, BigDecimal amount) {
        return storage.transfer(from, to, amount, BigDecimal.ZERO);
    }

    @Override
    public boolean transferBalance(UUID from, UUID to, BigDecimal amount) {
        return storage.transfer(from, to, BigDecimal.ZERO, amount);
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal diamondAmount, BigDecimal balanceAmount) {
        return storage.transfer(from, to, diamondAmount, balanceAmount);
    }

    @Override
    public boolean process(UUID player, BigDecimal diamondAmount, BigDecimal balanceAmount, BooleanSupplier action) {
        return storage.process(player, diamondAmount, balanceAmount, action);
    }
}