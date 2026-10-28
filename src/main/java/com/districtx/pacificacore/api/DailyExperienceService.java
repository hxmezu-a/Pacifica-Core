package com.districtx.pacificacore.api;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

/** Public API for locked, priced, cooldown-limited daily experience purchases. */
public interface DailyExperienceService {
    /** Checks whether the player has reached the configured unlock level. */
    boolean isUnlocked(UUID playerId);
    default boolean isUnlocked(Player player) { return player != null && isUnlocked(player.getUniqueId()); }
    /** Gets the scaled XP amount for the player's current 10-level tier. */
    double getExperienceAmount(UUID playerId);
    default double getExperienceAmount(Player player) { return player == null ? 0.0 : getExperienceAmount(player.getUniqueId()); }
    /** Gets the scaled economy price for the player's current 10-level tier. */
    double getPrice(UUID playerId);
    default double getPrice(Player player) { return player == null ? 0.0 : getPrice(player.getUniqueId()); }
    /** Gets the persistent cooldown remaining before another purchase is allowed. */
    Duration getRemainingCooldown(UUID playerId);
    default Duration getRemainingCooldown(Player player) {
        return player == null ? Duration.ZERO : getRemainingCooldown(player.getUniqueId());
    }
    /** Checks enabled state, unlock level, cooldown, and current balance. */
    boolean canPurchase(UUID playerId);
    default boolean canPurchase(Player player) { return player != null && canPurchase(player.getUniqueId()); }
    /** Attempts the economy withdrawal and XP grant, refunding failed grants. */
    DailyExperiencePurchaseResult purchase(Player player);
}