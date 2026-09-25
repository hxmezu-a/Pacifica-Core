package com.districtx.pacificacore.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public interface TransactionService {
    boolean transferDiamond(UUID from, UUID to, BigDecimal amount);

    boolean transferBalance(UUID from, UUID to, BigDecimal amount);

    boolean transfer(UUID from, UUID to, BigDecimal diamondAmount, BigDecimal balanceAmount);

    boolean process(UUID player, BigDecimal diamondAmount, BigDecimal balanceAmount, BooleanSupplier action);
}