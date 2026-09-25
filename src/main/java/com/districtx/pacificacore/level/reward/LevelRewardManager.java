package com.districtx.pacificacore.level.reward;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.DiamondCurrencyService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.api.LevelReward;
import com.districtx.pacificacore.api.LevelRewardContext;
import com.districtx.pacificacore.api.LevelRewardService;
import com.districtx.pacificacore.api.LevelRewardType;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.RewardResult;
import com.districtx.pacificacore.api.event.LevelRewardGrantEvent;
import com.districtx.pacificacore.storage.DatabaseManager;
import com.districtx.pacificacore.storage.LevelRewardClaimRepository;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Loads reward definitions from level-rewards.yml and persists one-time claims. */
public final class LevelRewardManager implements LevelRewardService {
    private final PacificaCore plugin;
    private final PlayerLevelService levels;
    private final EconomyService economy;
    private final DiamondCurrencyService diamonds;
    private final LevelRewardClaimRepository claims;

    public LevelRewardManager(PacificaCore plugin, DatabaseManager database, PlayerLevelService levels,
                              EconomyService economy, DiamondCurrencyService diamonds) {
        this.plugin = plugin;
        this.levels = levels;
        this.economy = economy;
        this.diamonds = diamonds;
        this.claims = new LevelRewardClaimRepository(plugin, database);
    }

    @Override
    public List<LevelReward> getLevelRewards(int level) {
        return loadRewards("levels." + level + ".rewards", "LEVEL:" + level);
    }

    @Override
    public List<LevelReward> getFiveLevelRewards(int level) {
        return level > 0 && level % 5 == 0
                ? loadRewards("milestones.every-5." + level + ".rewards", "EVERY_5:" + level)
                : List.of();
    }

    @Override
    public List<LevelReward> getHundredLevelRewards(int level) {
        return level > 0 && level % 100 == 0
                ? loadRewards("milestones.every-100." + level + ".rewards", "EVERY_100:" + level)
                : List.of();
    }

    @Override
    public List<LevelReward> getAllRewards(int level) {
        List<LevelReward> all = new ArrayList<>();
        List<String> order = plugin.getLevelRewardsConfig().getStringList("rewards.order");
        if (order.isEmpty()) order = List.of("LEVEL", "EVERY_5", "EVERY_100");
        for (String entry : order) {
            switch (entry.toUpperCase(Locale.ROOT)) {
                case "LEVEL" -> all.addAll(getLevelRewards(level));
                case "EVERY_5" -> all.addAll(getFiveLevelRewards(level));
                case "EVERY_100" -> all.addAll(getHundredLevelRewards(level));
                default -> plugin.getLogger().warning("Unknown level reward order entry: " + entry);
            }
        }
        return List.copyOf(all);
    }

    @Override
    public RewardResult grantLevelRewards(Player player, int level) {
        if (player == null || !plugin.getLevelRewardsConfig().getBoolean("rewards.enabled", true)) {
            return new RewardResult(0, 0, List.of());
        }
        int previousLevel = Math.max(levels.getMinimumLevel(), level - 1);
        LevelRewardContext context = new LevelRewardContext(player, level, previousLevel,
                levels.getExperience(player.getUniqueId()));
        int granted = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        for (LevelReward reward : getAllRewards(level)) {
            LevelRewardGrantEvent event = new LevelRewardGrantEvent(player, reward, context);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            if (!claims.claim(player.getUniqueId(), reward.getId())) continue;
            try {
                if (reward.execute(player, context)) {
                    granted++;
                    sendRewardMessage(player, reward, context, "granted");
                } else {
                    failed++;
                    failures.add(reward.getId());
                    sendRewardMessage(player, reward, context, "failed");
                    plugin.getLogger().warning("Level reward did not complete: " + reward.getId()
                            + " for " + player.getName());
                }
            } catch (RuntimeException exception) {
                failed++;
                failures.add(reward.getId());
                plugin.getLogger().warning("Level reward failed " + reward.getId() + ": " + exception.getMessage());
            }
        }
        return new RewardResult(granted, failed, failures);
    }

    @Override
    public RewardResult grantRewards(Player player, int level) {
        return grantLevelRewards(player, level);
    }

    @Override
    public RewardResult grantReward(Player player, LevelReward reward) {
        if (player == null || reward == null) {
            return new RewardResult(0, 0, List.of());
        }
        LevelRewardContext context = new LevelRewardContext(player, levels.getLevel(player.getUniqueId()),
                levels.getLevel(player.getUniqueId()), levels.getExperience(player.getUniqueId()));
        LevelRewardGrantEvent event = new LevelRewardGrantEvent(player, reward, context);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return new RewardResult(0, 0, List.of());
        if (!claims.claim(player.getUniqueId(), reward.getId())) return new RewardResult(0, 0, List.of());
        try {
            boolean successful = reward.execute(player, context);
            if (successful) sendRewardMessage(player, reward, context, "granted");
            else sendRewardMessage(player, reward, context, "failed");
            return successful ? new RewardResult(1, 0, List.of())
                    : new RewardResult(0, 1, List.of(reward.getId()));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Level reward failed " + reward.getId() + ": " + exception.getMessage());
            return new RewardResult(0, 1, List.of(reward.getId()));
        }
    }

    @Override
    public boolean hasRewards(int level) {
        return !getAllRewards(level).isEmpty();
    }

    @Override
    public boolean hasMilestoneRewards(int level) {
        return !getFiveLevelRewards(level).isEmpty() || !getHundredLevelRewards(level).isEmpty();
    }

    @Override
    public List<String> getDisplayLore(int level) {
        List<String> lore = new ArrayList<>();
        addLore(lore, "levels." + level + ".display.lore");
        if (level > 0 && level % 5 == 0) addLore(lore, "milestones.every-5." + level + ".display.lore");
        if (level > 0 && level % 100 == 0) addLore(lore, "milestones.every-100." + level + ".display.lore");
        return List.copyOf(lore);
    }

    private void addLore(List<String> target, String path) {
        target.addAll(plugin.getLevelRewardsConfig().getStringList(path));
    }

    private void sendRewardMessage(Player player, LevelReward reward, LevelRewardContext context, String status) {
        String message = plugin.getLevelRewardsConfig().getString("rewards.messages." + status, "");
        if (message == null || message.isBlank()) return;
        message = message.replace("%player%", player.getName())
                .replace("%level%", String.valueOf(context.getLevel()))
                .replace("%reward%", reward.getId())
                .replace("%type%", reward.getType().name());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private List<LevelReward> loadRewards(String path, String prefix) {
        List<?> definitions = plugin.getLevelRewardsConfig().getList(path);
        if (definitions == null) return List.of();
        List<LevelReward> loaded = new ArrayList<>();
        int index = 0;
        for (Object definition : definitions) {
            if (!(definition instanceof java.util.Map<?, ?> map)) continue;
            LevelReward reward = parseReward(map, prefix + ":" + index++);
            if (reward != null) loaded.add(reward);
        }
        return Collections.unmodifiableList(loaded);
    }

    private LevelReward parseReward(java.util.Map<?, ?> values, String id) {
        Object typeValue = values.get("type");
        if (typeValue == null) return null;
        LevelRewardType type;
        try {
            type = LevelRewardType.valueOf(String.valueOf(typeValue).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown level reward type '" + typeValue + "' in " + id);
            return null;
        }
        Object amountValue = values.get("amount");
        return build(type, stringValue(values.get("command")), decimal(amountValue == null ? "0" : amountValue.toString()),
                stringValue(values.get("material")), integer(amountValue, 1), id);
    }

    private LevelReward build(LevelRewardType type, String command, BigDecimal amount, String material,
                              int itemAmount, String id) {
        if (amount.signum() < 0) {
            plugin.getLogger().warning("Negative level reward amount ignored: " + id);
            return null;
        }
        return new ConfiguredLevelReward(plugin, economy, diamonds, id, type, command, amount, material, itemAmount);
    }

    private BigDecimal decimal(String value) {
        try {
            BigDecimal amount = new BigDecimal(value);
            return amount.signum() < 0 ? BigDecimal.ZERO : amount;
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}