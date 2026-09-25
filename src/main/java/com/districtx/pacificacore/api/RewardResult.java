package com.districtx.pacificacore.api;

import java.util.List;

/** Summary of the rewards attempted for a single level. */
public final class RewardResult {
    private final int granted;
    private final int failed;
    private final List<String> failures;

    public RewardResult(int granted, int failed, List<String> failures) {
        this.granted = granted;
        this.failed = failed;
        this.failures = List.copyOf(failures);
    }

    /** @return number of rewards successfully executed */
    public int getGrantedCount() { return granted; }
    /** @return number of rewards that failed to execute */
    public int getFailedCount() { return failed; }
    /** @return IDs of rewards that failed */
    public List<String> getFailures() { return failures; }
    /** @return whether every attempted reward succeeded */
    public boolean isSuccessful() { return failed == 0; }
}