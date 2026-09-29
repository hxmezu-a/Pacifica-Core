package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

/** Public service for configured advancement-style experience notifications. */
public interface AdvancementNotificationService {
    /** Sends the configured notification for a successful experience transaction. */
    void notifyExperienceGain(Player player, ExperienceGainResult result);

    /** @return whether experience advancement notifications are enabled */
    boolean isEnabled();
}