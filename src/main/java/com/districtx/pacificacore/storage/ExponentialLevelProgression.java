package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.LevelProgression;

final class ExponentialLevelProgression implements LevelProgression {
    private final int minimumLevel;
    private final double baseExperience;
    private final double growthRate;
    private final double logarithmicGrowth;

    ExponentialLevelProgression(int minimumLevel, double baseExperience, double growthRate) {
        this.minimumLevel = minimumLevel;
        this.baseExperience = baseExperience;
        this.growthRate = growthRate;
        this.logarithmicGrowth = Math.log(growthRate);
    }

    @Override
    public int getLevel(double experience) {
        if (!Double.isFinite(experience) || experience <= 0.0) return minimumLevel;
        double levelOffset = Math.floor(Math.log1p(experience * (growthRate - 1.0) / baseExperience)
                / logarithmicGrowth);
        if (!Double.isFinite(levelOffset) || levelOffset >= Integer.MAX_VALUE - minimumLevel) {
            return Integer.MAX_VALUE;
        }
        int level = minimumLevel + (int) Math.max(0.0, levelOffset);
        while (level > minimumLevel && getExperienceRequiredForLevel(level) > experience) level--;
        while (level < Integer.MAX_VALUE
                && getExperienceRequiredForLevel(level + 1) <= experience) level++;
        return level;
    }

    @Override
    public double getExperienceRequiredForLevel(int level) {
        long offset = Math.max(0L, (long) level - minimumLevel);
        if (offset == 0) return 0.0;
        return baseExperience * (Math.pow(growthRate, offset) - 1.0) / (growthRate - 1.0);
    }

    @Override
    public double getExperienceRequiredForNextLevel(int level) {
        long offset = Math.max(0L, (long) level - minimumLevel);
        return baseExperience * Math.pow(growthRate, offset);
    }

    @Override
    public double getExperienceToNextLevel(double experience) {
        int level = getLevel(experience);
        if (level == Integer.MAX_VALUE) return 0.0;
        return Math.max(0.0, getExperienceRequiredForLevel(level + 1) - Math.max(0.0, experience));
    }

    @Override
    public double getProgressToNextLevel(double experience) {
        double currentExperience = Double.isFinite(experience) ? Math.max(0.0, experience) : 0.0;
        int level = getLevel(currentExperience);
        double start = getExperienceRequiredForLevel(level);
        double requirement = getExperienceRequiredForNextLevel(level);
        if (!Double.isFinite(requirement) || requirement <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (currentExperience - start) / requirement));
    }

    @Override public int getMinimumLevel() { return minimumLevel; }
    @Override public int getMaximumLevel() { return Integer.MAX_VALUE; }
}