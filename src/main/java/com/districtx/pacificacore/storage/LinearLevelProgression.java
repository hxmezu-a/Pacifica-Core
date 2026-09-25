package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.LevelProgression;

final class LinearLevelProgression implements LevelProgression {
    private final int minimumLevel;
    private final int maximumLevel;
    private final double experiencePerLevel;

    LinearLevelProgression(int minimumLevel, int maximumLevel, double experiencePerLevel) {
        this.minimumLevel = minimumLevel;
        this.maximumLevel = maximumLevel;
        this.experiencePerLevel = experiencePerLevel;
    }

    @Override
    public int getLevel(double experience) {
        if (!Double.isFinite(experience) || experience <= 0.0) return minimumLevel;
        double levelOffset = Math.floor(experience / experiencePerLevel);
        if (levelOffset >= maximumLevel - minimumLevel) return maximumLevel;
        return minimumLevel + (int) levelOffset;
    }

    @Override
    public double getExperienceRequiredForLevel(int level) {
        int clampedLevel = Math.max(minimumLevel, Math.min(maximumLevel, level));
        return (clampedLevel - minimumLevel) * experiencePerLevel;
    }

    @Override
    public double getExperienceToNextLevel(double experience) {
        int level = getLevel(experience);
        if (level >= maximumLevel) return 0.0;
        return Math.max(0.0, getExperienceRequiredForLevel(level + 1) - Math.max(0.0, experience));
    }

    @Override
    public double getProgressToNextLevel(double experience) {
        int level = getLevel(experience);
        if (level >= maximumLevel) return 1.0;
        double current = Double.isFinite(experience) ? Math.max(0.0, experience) : 0.0;
        double start = getExperienceRequiredForLevel(level);
        return Math.max(0.0, Math.min(1.0, (current - start) / experiencePerLevel));
    }

    @Override public int getMinimumLevel() { return minimumLevel; }
    @Override public int getMaximumLevel() { return maximumLevel; }
}