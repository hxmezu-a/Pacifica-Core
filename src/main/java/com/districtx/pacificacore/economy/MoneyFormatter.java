package com.districtx.pacificacore.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats display-only values with HALF_UP rounding; suffixes progress through mil, bil, Tril, Quad,
 * Quint, Sext, Sept, Oct, Non, and Dec, then use a 10^N magnitude label.
 */
public final class MoneyFormatter {
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final String[] SUFFIXES = {"mil", "bil", "Tril", "Quad", "Quint", "Sext", "Sept", "Oct", "Non", "Dec"};

    private MoneyFormatter() { }

    public static String format(BigDecimal amount) {
        if (amount == null) return "0";
        BigDecimal absolute = amount.abs();
        if (absolute.compareTo(MILLION) < 0) return grouped(amount);
        int integerDigits = Math.max(1, absolute.precision() - absolute.scale());
        int magnitude = ((integerDigits - 1) / 3) * 3;
        BigDecimal scaled = absolute.movePointLeft(magnitude).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            magnitude += 3;
            scaled = absolute.movePointLeft(magnitude).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        int suffixIndex = magnitude / 3 - 2;
        String suffix = suffixIndex < SUFFIXES.length ? SUFFIXES[suffixIndex] : "10^" + magnitude;
        return (amount.signum() < 0 ? "-" : "") + scaled.toPlainString() + " " + suffix;
    }

    private static String grouped(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.########", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }
}