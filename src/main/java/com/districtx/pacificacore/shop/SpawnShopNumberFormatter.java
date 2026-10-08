package com.districtx.pacificacore.shop;

import java.util.Locale;

public final class SpawnShopNumberFormatter {
    private SpawnShopNumberFormatter() {
    }

    public static String formatPrice(double price) {
        return String.format(Locale.US, "%.1f", price);
    }
}