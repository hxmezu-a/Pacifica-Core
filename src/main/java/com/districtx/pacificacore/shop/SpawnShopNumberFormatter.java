package com.districtx.pacificacore.shop;

import com.districtx.pacificacore.economy.MoneyFormatter;

import java.math.BigDecimal;

public final class SpawnShopNumberFormatter {
    private SpawnShopNumberFormatter() {
    }

    public static String formatPrice(double price) {
        return MoneyFormatter.format(BigDecimal.valueOf(price));
    }
}