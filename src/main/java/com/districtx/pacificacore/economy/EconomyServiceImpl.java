package com.districtx.pacificacore.economy;

import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.storage.CurrencyStorage;

import java.math.BigDecimal;
import java.util.UUID;

public final class EconomyServiceImpl implements EconomyService {
    private final CurrencyStorage storage;

    public EconomyServiceImpl(CurrencyStorage storage) {
        this.storage = storage;
    }

    @Override
    public BigDecimal getBalance(UUID player) {
        return storage.getBalance(player);
    }

    @Override
    public boolean has(UUID player, BigDecimal amount) {
        return storage.hasBalance(player, amount);
    }

    @Override
    public boolean deposit(UUID player, BigDecimal amount) {
        return storage.depositBalance(player, amount);
    }

    @Override
    public boolean withdraw(UUID player, BigDecimal amount) {
        return storage.withdrawBalance(player, amount);
    }

    @Override
    public boolean setBalance(UUID player, BigDecimal amount) {
        return storage.setBalance(player, amount);
    }

    @Override
    public boolean resetBalance(UUID player) {
        return storage.resetBalance(player);
    }
}