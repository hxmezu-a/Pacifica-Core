package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.LevelProgression;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

final class ExponentialLevelProgression implements LevelProgression {
    private static final MathContext CALCULATION_CONTEXT = new MathContext(128, RoundingMode.HALF_EVEN);
    private final int minimumLevel;
    private final double baseExperience;
    private final double growthRate;
    private final double logarithmicGrowth;
    private final BigDecimal decimalBaseExperience;
    private final BigDecimal decimalGrowthRate;

    ExponentialLevelProgression(int minimumLevel, double baseExperience, double growthRate) {
        this.minimumLevel = minimumLevel;
        this.baseExperience = baseExperience;
        this.growthRate = growthRate;
        this.logarithmicGrowth = Math.log(growthRate);
        this.decimalBaseExperience = BigDecimal.valueOf(baseExperience);
        this.decimalGrowthRate = BigDecimal.valueOf(growthRate);
    }

    @Override
    public int getLevel(double experience) {
        if (!Double.isFinite(experience) || experience <= 0.0) return minimumLevel;
        return getLevel(BigDecimal.valueOf(experience));
    }

    int getLevel(BigDecimal experience) {
        if (experience == null || experience.signum() <= 0) return minimumLevel;
        double logExperience = logarithm(experience);
        double logArgument = logExperience + Math.log(growthRate - 1.0) - Math.log(baseExperience);
        double levelOffset = Math.floor((logArgument > 40.0
                ? logArgument : Math.log1p(Math.exp(logArgument))) / logarithmicGrowth);
        if (!Double.isFinite(levelOffset) || levelOffset >= Integer.MAX_VALUE - minimumLevel) {
            return Integer.MAX_VALUE;
        }
        int level = minimumLevel + (int) Math.max(0.0, levelOffset);
        while (level > minimumLevel && getExperienceRequiredForLevelExact(level).compareTo(experience) > 0) level--;
        while (level < Integer.MAX_VALUE
                && getExperienceRequiredForLevelExact(level + 1).compareTo(experience) <= 0) level++;
        return level;
    }

    @Override
    public double getExperienceRequiredForLevel(int level) {
        return getExperienceRequiredForLevelExact(level).doubleValue();
    }

    BigDecimal getExperienceRequiredForLevelExact(int level) {
        long offset = Math.max(0L, (long) level - minimumLevel);
        if (offset == 0) return BigDecimal.ZERO;
        BigDecimal numerator = power(offset).subtract(BigDecimal.ONE);
        BigDecimal denominator = decimalGrowthRate.subtract(BigDecimal.ONE);
        try {
            return decimalBaseExperience.multiply(numerator).divide(denominator);
        } catch (ArithmeticException exception) {
            return decimalBaseExperience.multiply(numerator).divide(denominator, CALCULATION_CONTEXT);
        }
    }

    @Override
    public double getExperienceRequiredForNextLevel(int level) {
        return getExperienceRequiredForNextLevelExact(level).doubleValue();
    }

    BigDecimal getExperienceRequiredForNextLevelExact(int level) {
        long offset = Math.max(0L, (long) level - minimumLevel);
        return decimalBaseExperience.multiply(power(offset));
    }

    @Override
    public double getExperienceToNextLevel(double experience) {
        if (!Double.isFinite(experience)) return 0.0;
        return getExperienceToNextLevelExact(BigDecimal.valueOf(Math.max(0.0, experience))).doubleValue();
    }

    BigDecimal getExperienceToNextLevelExact(BigDecimal experience) {
        int level = getLevel(experience);
        if (level == Integer.MAX_VALUE) return BigDecimal.ZERO;
        return getExperienceRequiredForLevelExact(level + 1).subtract(experience).max(BigDecimal.ZERO);
    }

    @Override
    public double getProgressToNextLevel(double experience) {
        double currentExperience = Double.isFinite(experience) ? Math.max(0.0, experience) : 0.0;
        return getProgressToNextLevel(BigDecimal.valueOf(currentExperience));
    }

    double getProgressToNextLevel(BigDecimal experience) {
        BigDecimal currentExperience = experience == null ? BigDecimal.ZERO : experience.max(BigDecimal.ZERO);
        int level = getLevel(currentExperience);
        BigDecimal start = getExperienceRequiredForLevelExact(level);
        BigDecimal requirement = getExperienceRequiredForNextLevelExact(level);
        if (requirement.signum() <= 0) return 0.0;
        double progress = currentExperience.subtract(start).max(BigDecimal.ZERO)
                .divide(requirement, CALCULATION_CONTEXT).doubleValue();
        return Math.max(0.0, Math.min(1.0, progress));
    }

    float getExpBarProgress(BigDecimal experience) { return (float) getProgressToNextLevel(experience); }

    private BigDecimal power(long exponent) {
        BigDecimal result = BigDecimal.ONE;
        BigDecimal factor = decimalGrowthRate;
        long remaining = exponent;
        while (remaining > 0) {
            if ((remaining & 1L) != 0) result = result.multiply(factor, CALCULATION_CONTEXT);
            remaining >>= 1;
            if (remaining > 0) factor = factor.multiply(factor, CALCULATION_CONTEXT);
        }
        return result;
    }

    private double logarithm(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        String digits = normalized.unscaledValue().abs().toString();
        int leadingLength = Math.min(16, digits.length());
        double leading = Double.parseDouble(digits.substring(0, leadingLength))
                / Math.pow(10.0, leadingLength - 1);
        long exponent = (long) normalized.precision() - normalized.scale() - 1L;
        return exponent * Math.log(10.0) + Math.log(leading);
    }

    @Override public int getMinimumLevel() { return minimumLevel; }
    @Override public int getMaximumLevel() { return Integer.MAX_VALUE; }
}