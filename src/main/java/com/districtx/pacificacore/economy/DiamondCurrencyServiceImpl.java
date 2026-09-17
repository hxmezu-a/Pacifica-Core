package com.districtx.pacificacore.economy;

import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.storage.CurrencyStorage;

import java.math.BigDecimal;
import java.util.UUID;

public final class DiamondCurrencyServiceImpl implements DiamondCurrencyService {
    private final CurrencyStorage storage;

    public DiamondCurrencyServiceImpl(CurrencyStorage storage) {
        this.storage = storage;
    }

    @Override
    public String getCurrencyName() {
        return "Diamond";
    }

    @Override
    public BigDecimal getBalance(UUID player) {
        return storage.getDiamond(player);
    }

    @Override
    public boolean has(UUID player, BigDecimal amount) {
        return storage.hasDiamond(player, amount);
    }

    @Override
    public boolean deposit(UUID player, BigDecimal amount) {
        return storage.depositDiamond(player, amount);
    }

    @Override
    public boolean withdraw(UUID player, BigDecimal amount) {
        return storage.withdrawDiamond(player, amount);
    }

    @Override
    public boolean setBalance(UUID player, BigDecimal amount) {
        return storage.setDiamond(player, amount);
    }

    @Override
    public boolean resetBalance(UUID player) {
        return storage.resetDiamond(player);
    }
}