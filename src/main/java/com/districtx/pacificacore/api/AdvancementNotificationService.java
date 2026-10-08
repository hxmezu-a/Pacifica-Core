package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

/** Public service for configured advancement-style experience notifications. */
public interface AdvancementNotificationService {
    /** Sends the configured notification for a successful experience transaction. */
    void notifyExperienceGain(Player player, ExperienceGainResult result);

    /** Restores deferred notifications after the player has respawned. */
    default void refresh(Player player) {
    }

    /** Clears player-specific temporary notification state. */
    default void cleanup(Player player) {
    }

    /** @return whether experience advancement notifications are enabled */
    boolean isEnabled();
}