package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.LevelProgression;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

final class ExponentialLevelProgression implements LevelProgression {
    private static final MathContext CALCULATION_CONTEXT = new MathContext(128, RoundingMode.HALF_EVEN);
    private final int minimumLevel;
    private final int maximumLevel;
    private final BigDecimal[] totalExperienceByLevel;
    private final BigDecimal[] nextLevelCostByLevel;

    ExponentialLevelProgression(int minimumLevel, int maximumLevel, double baseExperience, double growthRate) {
        this.minimumLevel = minimumLevel;
        this.maximumLevel = maximumLevel;
        BigDecimal decimalBaseExperience = BigDecimal.valueOf(baseExperience);
        BigDecimal decimalGrowthRate = BigDecimal.valueOf(growthRate);
        int levelCount = maximumLevel - minimumLevel + 1;
        this.totalExperienceByLevel = new BigDecimal[levelCount];
        this.nextLevelCostByLevel = new BigDecimal[levelCount];
        totalExperienceByLevel[0] = BigDecimal.ZERO;
        nextLevelCostByLevel[0] = levelCount == 1 ? BigDecimal.ZERO : decimalBaseExperience;
        for (int index = 1; index < levelCount; index++) {
            totalExperienceByLevel[index] = totalExperienceByLevel[index - 1]
                    .add(nextLevelCostByLevel[index - 1]);
            nextLevelCostByLevel[index] = index == levelCount - 1 ? BigDecimal.ZERO
                    : nextLevelCostByLevel[index - 1].multiply(decimalGrowthRate)
                    .setScale(0, RoundingMode.CEILING);
        }
    }

    @Override
    public int getLevel(double experience) {
        if (!Double.isFinite(experience) || experience <= 0.0) return minimumLevel;
        return getLevel(BigDecimal.valueOf(experience));
    }

    int getLevel(BigDecimal experience) {
        if (experience == null || experience.signum() <= 0) return minimumLevel;
        int low = 0;
        int high = totalExperienceByLevel.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (totalExperienceByLevel[middle].compareTo(experience) <= 0) low = middle + 1;
            else high = middle;
        }
        return minimumLevel + Math.max(0, low - 1);
    }

    @Override
    public double getExperienceRequiredForLevel(int level) {
        return getExperienceRequiredForLevelExact(level).doubleValue();
    }

    BigDecimal getExperienceRequiredForLevelExact(int level) {
        int boundedLevel = Math.max(minimumLevel, Math.min(maximumLevel, level));
        return totalExperienceByLevel[boundedLevel - minimumLevel];
    }

    @Override
    public double getExperienceRequiredForNextLevel(int level) {
        return getExperienceRequiredForNextLevelExact(level).doubleValue();
    }

    BigDecimal getExperienceRequiredForNextLevelExact(int level) {
        int boundedLevel = Math.max(minimumLevel, Math.min(maximumLevel, level));
        return nextLevelCostByLevel[boundedLevel - minimumLevel];
    }

    @Override
    public double getExperienceToNextLevel(double experience) {
        if (!Double.isFinite(experience)) return 0.0;
        return getExperienceToNextLevelExact(BigDecimal.valueOf(Math.max(0.0, experience))).doubleValue();
    }

    BigDecimal getExperienceToNextLevelExact(BigDecimal experience) {
        int level = getLevel(experience);
        if (level >= maximumLevel) return BigDecimal.ZERO;
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
        if (level >= maximumLevel) return 1.0;
        BigDecimal start = getExperienceRequiredForLevelExact(level);
        BigDecimal requirement = getExperienceRequiredForNextLevelExact(level);
        if (requirement.signum() <= 0) return 0.0;
        double progress = currentExperience.subtract(start).max(BigDecimal.ZERO)
                .divide(requirement, CALCULATION_CONTEXT).doubleValue();
        return Math.max(0.0, Math.min(1.0, progress));
    }

    float getExpBarProgress(BigDecimal experience) { return (float) getProgressToNextLevel(experience); }

    @Override public int getMinimumLevel() { return minimumLevel; }
    @Override public int getMaximumLevel() { return maximumLevel; }
}