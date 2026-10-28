package com.districtx.pacificacore.api;

import java.time.Duration;

/** Result of attempting to purchase Daily XP. */
public final class DailyExperiencePurchaseResult {
    private final boolean successful;
    private final double experience;
    private final double price;
    private final Duration cooldown;
    private final String failureReason;

    public DailyExperiencePurchaseResult(boolean successful, double experience, double price,
                                         Duration cooldown, String failureReason) {
        this.successful = successful;
        this.experience = experience;
        this.price = price;
        this.cooldown = cooldown;
        this.failureReason = failureReason;
    }

    public boolean isSuccessful() { return successful; }
    public double getExperience() { return experience; }
    public double getPrice() { return price; }
    public Duration getCooldown() { return cooldown; }
    public String getFailureReason() { return failureReason; }
}