package com.districtx.pacificacore.level;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.DailyExperiencePurchaseResult;
import com.districtx.pacificacore.api.DailyExperienceService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.api.ExperienceGainResult;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.event.DailyExperiencePurchaseEvent;
import com.districtx.pacificacore.storage.DailyExperienceRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persistent Daily XP purchase flow with compensating refunds on failed XP grants. */
public final class DailyExperienceManager implements DailyExperienceService {
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([smhd])", Pattern.CASE_INSENSITIVE);
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final EconomyService economy;
    private final DailyExperienceRepository repository;

    public DailyExperienceManager(PacificaCore plugin, PlayerLevelService levels, EconomyService economy,
                                  DailyExperienceRepository repository) {
        this.plugin = plugin;
        this.levels = levels;
        this.economy = economy;
        this.repository = repository;
    }

    @Override
    public boolean isUnlocked(UUID playerId) {
        return playerId != null && levels.getLevel(playerId)
                >= plugin.getLevelConfig().getInt("daily-xp.unlock-level", 10);
    }

    @Override
    public double getExperienceAmount(UUID playerId) {
        return scaledAmount(playerId, "daily-xp.base-xp", "daily-xp.xp-increase-per-10-levels", 100.0);
    }

    @Override
    public double getPrice(UUID playerId) {
        double scaled = scaledAmount(playerId, "daily-xp.base-price", "daily-xp.price-increase-per-10-levels", 2500.0);
        return Double.isFinite(scaled) ? BigDecimal.valueOf(scaled).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : scaled;
    }

    @Override
    public Duration getRemainingCooldown(UUID playerId) {
        if (playerId == null) return Duration.ZERO;
        long lastPurchase = repository.getLastPurchase(playerId);
        if (lastPurchase < 0L) return Duration.ofMillis(Long.MAX_VALUE);
        Duration cooldown = getConfiguredCooldown();
        long remaining = cooldown.toMillis() - Math.max(0L, System.currentTimeMillis() - lastPurchase);
        return Duration.ofMillis(Math.max(0L, remaining));
    }

    @Override
    public boolean canPurchase(UUID playerId) {
        if (playerId == null || !plugin.getLevelConfig().getBoolean("daily-xp.enabled", true)
                || !isUnlocked(playerId) || !getRemainingCooldown(playerId).isZero()) return false;
        double experience = getExperienceAmount(playerId);
        double price = getPrice(playerId);
        return Double.isFinite(experience) && experience > 0.0 && Double.isFinite(price) && price >= 0.0
                && economy.has(playerId, BigDecimal.valueOf(price));
    }

    @Override
    public DailyExperiencePurchaseResult purchase(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            try {
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> purchaseOnMainThread(player)).get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure("interrupted", 0.0, 0.0);
            } catch (java.util.concurrent.ExecutionException | RuntimeException exception) {
                plugin.getLogger().severe("Could not schedule Daily XP purchase: " + exception.getMessage());
                return failure("purchase-failed", 0.0, 0.0);
            }
        }
        return purchaseOnMainThread(player);
    }

    private synchronized DailyExperiencePurchaseResult purchaseOnMainThread(Player player) {
        if (player == null) return failure("player-unavailable", 0.0, 0.0);
        UUID playerId = player.getUniqueId();
        if (!plugin.getLevelConfig().getBoolean("daily-xp.enabled", true)) {
            return failure("disabled", 0.0, 0.0);
        }
        if (!isUnlocked(playerId)) return failure("locked", 0.0, 0.0);
        Duration remaining = getRemainingCooldown(playerId);
        if (!remaining.isZero()) return new DailyExperiencePurchaseResult(false, 0.0, 0.0, remaining, "cooldown");
        double experience = getExperienceAmount(playerId);
        double price = getPrice(playerId);
        if (!Double.isFinite(experience) || experience <= 0.0 || !Double.isFinite(price) || price < 0.0) {
            return failure("invalid-configuration", experience, price);
        }
        BigDecimal cost = BigDecimal.valueOf(price);
        if (!economy.has(playerId, cost)) return failure("insufficient-funds", experience, price);
        DailyExperiencePurchaseEvent event = new DailyExperiencePurchaseEvent(player, experience, price);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return failure("cancelled", experience, price);
        if (!economy.withdraw(playerId, cost)) return failure("withdrawal-failed", experience, price);

        long purchasedAt = System.currentTimeMillis();
        if (!repository.recordPurchase(playerId, purchasedAt)) {
            if (!economy.deposit(playerId, cost)) {
                plugin.getLogger().severe("Could not refund Daily XP withdrawal for " + playerId);
            }
            return failure("persistence-failed", experience, price);
        }
        ExperienceGainResult gain = levels.addExperience(player, experience, ExperienceSource.DAILY_XP);
        if (gain.getExperienceAdded() <= 0.0) {
            if (!repository.reset(playerId)) {
                plugin.getLogger().severe("Could not clear cooldown after a failed Daily XP grant for " + playerId);
            }
            if (!economy.deposit(playerId, cost)) {
                plugin.getLogger().severe("Could not refund a failed Daily XP purchase for " + playerId);
            }
            return failure("experience-grant-failed", experience, price);
        }
        return new DailyExperiencePurchaseResult(true, gain.getExperienceAdded(), price, getConfiguredCooldown(), null);
    }

    private double scaledAmount(UUID playerId, String basePath, String increasePath, double fallback) {
        double base = plugin.getLevelConfig().getDouble(basePath, fallback);
        double increase = plugin.getLevelConfig().getDouble(increasePath, 0.0);
        if (!Double.isFinite(base) || base < 0.0 || !Double.isFinite(increase) || increase <= -1.0) {
            return Double.NaN;
        }
        int unlockLevel = Math.max(1, plugin.getLevelConfig().getInt("daily-xp.unlock-level", 10));
        int level = playerId == null ? unlockLevel : levels.getLevel(playerId);
        int tiers = Math.max(0, (level - unlockLevel) / 10);
        double result = base * Math.pow(1.0 + increase, tiers);
        return Double.isFinite(result) && result >= 0.0 ? result : Double.MAX_VALUE;
    }

    private Duration getConfiguredCooldown() {
        String configured = plugin.getLevelConfig().getString("daily-xp.cooldown", "24h");
        try {
            if (configured != null && configured.toUpperCase().startsWith("P")) {
                Duration duration = Duration.parse(configured);
                if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("invalid duration");
                duration.toMillis();
                return duration;
            }
            Matcher matcher = DURATION_PART.matcher(configured == null ? "" : configured.replace(" ", ""));
            Duration duration = Duration.ZERO;
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() != end) throw new IllegalArgumentException("invalid duration");
                long amount = Long.parseLong(matcher.group(1));
                duration = duration.plus(switch (matcher.group(2).toLowerCase()) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    case "d" -> Duration.ofDays(amount);
                    default -> Duration.ZERO;
                });
                end = matcher.end();
            }
            if (end != (configured == null ? 0 : configured.replace(" ", "").length()) || duration.isZero()) {
                throw new IllegalArgumentException("invalid duration");
            }
            duration.toMillis();
            return duration;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid Daily XP cooldown; using 24h.");
            return Duration.ofHours(24);
        }
    }

    private DailyExperiencePurchaseResult failure(String reason, double experience, double price) {
        return new DailyExperiencePurchaseResult(false, experience, price, Duration.ZERO, reason);
    }
}