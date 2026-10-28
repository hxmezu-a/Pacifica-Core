package com.districtx.pacificacore.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface DiamondCurrencyService {
    String getCurrencyName();

    BigDecimal getBalance(UUID player);

    boolean has(UUID player, BigDecimal amount);

    boolean deposit(UUID player, BigDecimal amount);

    boolean withdraw(UUID player, BigDecimal amount);

    boolean setBalance(UUID player, BigDecimal amount);

    boolean resetBalance(UUID player);
}