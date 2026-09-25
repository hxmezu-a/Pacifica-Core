package com.districtx.pacificacore.api;

/** Result of a Prestige operation. */
public final class PrestigeResult {
    private final boolean successful;
    private final int prestige;
    private final String failureReason;

    public PrestigeResult(boolean successful, int prestige, String failureReason) {
        this.successful = successful;
        this.prestige = prestige;
        this.failureReason = failureReason;
    }

    public boolean isSuccessful() { return successful; }
    public int getPrestige() { return prestige; }
    public String getFailureReason() { return failureReason; }
}