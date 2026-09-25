package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.ExperienceGainResult;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.api.LevelProgression;
import com.districtx.pacificacore.api.PlayerLevelService;
import com.districtx.pacificacore.api.LevelRewardService;
import com.districtx.pacificacore.api.RankExperienceBonusService;
import com.districtx.pacificacore.api.LevelMenuService;
import com.districtx.pacificacore.api.event.PlayerExperienceGainEvent;
import com.districtx.pacificacore.api.event.PlayerLevelUpEvent;
import com.districtx.pacificacore.api.event.PrestigeUnlockEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Core-owned implementation of the public Player Level service. */
public final class PlayerLevelManager implements PlayerLevelService {
    private final PacificaCore plugin;
    private final PlayerLevelRepository repository;
    private final PendingLevelRewardRepository pendingRewards;
    private volatile LevelProgression progression;
    private volatile LevelRewardService rewardService;
    private volatile RankExperienceBonusService rankBonuses;
    private volatile LevelMenuService levelMenus;

    /**
     * Creates the persistent Player Level service.
     *
     * @param plugin owning Pacifica-Core plugin
     * @param database existing Pacifica-Core database manager
     */
    public PlayerLevelManager(PacificaCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.repository = new PlayerLevelRepository(plugin, database);
        this.pendingRewards = new PendingLevelRewardRepository(plugin, database);
        reload();
    }

    /** Reloads level range and progression settings without changing stored experience. */
    public void reload() {
        int minimum = Math.max(1, plugin.getLevelConfig().getInt("leveling.minimum-level", 1));
        double baseExperience = plugin.getLevelConfig().getDouble("leveling.progression.base-xp", 100.0);
        if (!Double.isFinite(baseExperience) || baseExperience <= 0.0) baseExperience = 100.0;
        double growthRate = plugin.getLevelConfig().getDouble("leveling.progression.growth-rate", 1.25);
        if (!Double.isFinite(growthRate) || growthRate <= 1.0) growthRate = 1.25;
        if (!plugin.getLevelConfig().getBoolean("leveling.infinite-levels", true)) {
            plugin.getLogger().warning("Finite level caps are no longer supported; Player Level remains unlimited.");
        }
        progression = new ExponentialLevelProgression(minimum, baseExperience, growthRate);
    }

    /**
     * Creates the player's default record if one does not exist.
     *
     * @param playerId player UUID
     * @return whether initialization completed successfully
     */
    public boolean initializePlayer(UUID playerId) {
        return repository.initialize(playerId);
    }

    public void setRewardService(LevelRewardService rewardService) {
        this.rewardService = rewardService;
    }

    public void setRankExperienceBonusService(RankExperienceBonusService rankBonuses) {
        this.rankBonuses = rankBonuses;
    }

    public void setLevelMenuService(LevelMenuService levelMenus) {
        this.levelMenus = levelMenus;
    }

    @Override public double getExperience(UUID playerId) { return repository.getExperience(playerId); }

    @Override
    public int getLevel(UUID playerId) {
        return progression.getLevel(getExperience(playerId));
    }

    @Override
    public double getExperienceToNextLevel(UUID playerId) {
        return progression.getExperienceToNextLevel(getExperience(playerId));
    }

    @Override
    public double getExperienceRequiredForLevel(int level) {
        return progression.getExperienceRequiredForLevel(level);
    }

    @Override
    public double getProgressToNextLevel(UUID playerId) {
        return progression.getProgressToNextLevel(getExperience(playerId));
    }

    @Override public int getMinimumLevel() { return progression.getMinimumLevel(); }
    @Override public int getMaximumLevel() { return progression.getMaximumLevel(); }
    @Override public LevelProgression getProgression() { return progression; }

    @Override
    public boolean addExperience(UUID playerId, double amount) {
        return addExperience(playerId, amount, ExperienceSource.API).getExperienceAdded() > 0.0;
    }

    @Override
    public boolean addExperience(Player player, double amount) {
        return player != null && addExperience(player.getUniqueId(), amount, ExperienceSource.API)
                .getExperienceAdded() > 0.0;
    }

    @Override
    public ExperienceGainResult addExperience(Player player, double amount, ExperienceSource source) {
        return player == null ? emptyResult(null, source) : addExperience(player.getUniqueId(), amount, source);
    }

    @Override
    public ExperienceGainResult addExperience(UUID playerId, double amount, ExperienceSource source) {
        ExperienceSource effectiveSource = source == null ? ExperienceSource.OTHER : source;
        double previous = getExperience(playerId);
        if (playerId == null || !Double.isFinite(amount) || amount <= 0.0) {
            return result(playerId, previous, 0.0, previous, progression.getLevel(previous),
                    progression.getLevel(previous), effectiveSource);
        }
        return onMainThread(() -> addExperienceOnMainThread(playerId, amount, effectiveSource),
                result(playerId, previous, 0.0, previous, progression.getLevel(previous),
                        progression.getLevel(previous), effectiveSource));
    }

    @Override
    public boolean removeExperience(UUID playerId, double amount) {
        if (playerId == null || !Double.isFinite(amount) || amount < 0.0) return false;
        return onMainThread(() -> {
            PlayerLevelRepository.Change change = repository.removeExperience(playerId, amount);
            if (!change.successful()) return false;
            fireLevelUp(playerId, change.previous(), change.current());
            refreshMenu(playerId);
            return true;
        }, false);
    }

    @Override
    public boolean setExperience(UUID playerId, double amount) {
        if (playerId == null || !Double.isFinite(amount) || amount < 0.0) return false;
        return onMainThread(() -> {
            PlayerLevelRepository.Change change = repository.setExperience(playerId, amount);
            if (!change.successful()) return false;
            fireLevelUp(playerId, change.previous(), change.current());
            refreshMenu(playerId);
            return true;
        }, false);
    }

    @Override
    public boolean setLevel(UUID playerId, int level) {
        if (playerId == null || level < getMinimumLevel()) return false;
        return setExperience(playerId, getExperienceRequiredForLevel(level));
    }

    @Override
    public boolean hasExperience(UUID playerId, double amount) {
        return playerId != null && Double.isFinite(amount) && amount >= 0.0
                && getExperience(playerId) >= amount;
    }

    private synchronized ExperienceGainResult addExperienceOnMainThread(UUID playerId, double proposedAmount,
                                                                         ExperienceSource source) {
        double before = repository.getExperience(playerId);
        int previousLevel = progression.getLevel(before);
        Player player = Bukkit.getPlayer(playerId);
        double amount = proposedAmount;
        RankExperienceBonusService bonusService = rankBonuses;
        if (bonusService != null) amount = bonusService.applyBonus(playerId, source, amount);
        if (player != null) {
            PlayerExperienceGainEvent event = new PlayerExperienceGainEvent(
                    player, amount, source, before, previousLevel, progression);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return result(playerId, before, 0.0, before, previousLevel, previousLevel, source);
            }
            amount = event.getAmount();
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return result(playerId, before, 0.0, before, previousLevel, previousLevel, source);
        }
        PlayerLevelRepository.Change change = repository.addExperience(playerId, amount);
        if (!change.successful()) {
            return result(playerId, before, 0.0, before, previousLevel, previousLevel, source);
        }
        int newLevel = progression.getLevel(change.current());
        ExperienceGainResult result = result(playerId, change.previous(), change.current() - change.previous(),
                change.current(), progression.getLevel(change.previous()), newLevel, source);
        fireLevelUp(playerId, change.previous(), change.current());
        notifyExperience(player, amount, change.current(), newLevel, source);
        refreshMenu(playerId);
        if (plugin.getLevelConfig().getBoolean("leveling.debug.xp", false)) {
            plugin.getLogger().info("Player Level XP: player=" + playerId + " source=" + source
                    + " amount=+" + format(amount) + " previous=" + format(change.previous())
                    + " new=" + format(change.current()) + " level=" + result.getPreviousLevel()
                    + "->" + result.getNewLevel());
        }
        return result;
    }

    private void fireLevelUp(UUID playerId, double previousExperience, double newExperience) {
        int previousLevel = progression.getLevel(previousExperience);
        int newLevel = progression.getLevel(newExperience);
        if (newLevel <= previousLevel) return;
        boolean persisted = pendingRewards.record(playerId, previousLevel, newLevel);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        Bukkit.getPluginManager().callEvent(new PlayerLevelUpEvent(player, previousLevel, newLevel));
        int prestigeUnlock = plugin.getLevelConfig().getInt("prestige.unlock-level", 100);
        if (previousLevel < prestigeUnlock && newLevel >= prestigeUnlock) {
            Bukkit.getPluginManager().callEvent(new PrestigeUnlockEvent(player, prestigeUnlock));
        }
        if (persisted) processPendingLevelRewards(player);
        else grantLevelRewardsRange(player, previousLevel, newLevel);
        if (!plugin.getLevelConfig().getBoolean("leveling.messages.level-up.enabled", true)) return;
        String message = plugin.getLevelConfig().getString("leveling.messages.level-up.message", "");
        if (message.isEmpty()) return;
        message = message.replace("%old_level%", String.valueOf(previousLevel))
                .replace("%new_level%", String.valueOf(newLevel))
                .replace("%level%", String.valueOf(newLevel))
                .replace("%exp%", format(newExperience))
                .replace("%exp_to_next_level%", format(progression.getExperienceToNextLevel(newExperience)));
        player.sendMessage(color(message));
    }

    public void processPendingLevelRewards(Player player) {
        if (player == null) return;
        PendingLevelRewardRepository.Range range = pendingRewards.get(player.getUniqueId());
        if (range == null) return;
        LevelRewardService rewards = rewardService;
        if (rewards == null || !plugin.getLevelRewardsConfig().getBoolean("rewards.enabled", true)) return;
        grantLevelRewardsRange(player, range.previousLevel(), range.newLevel());
        pendingRewards.clear(player.getUniqueId());
    }

    private void grantLevelRewardsRange(Player player, int previousLevel, int lastLevel) {
        LevelRewardService rewards = rewardService;
        if (rewards == null || !plugin.getLevelRewardsConfig().getBoolean("rewards.enabled", true)) return;
        int startLevel = plugin.getLevelRewardsConfig().getBoolean("rewards.process-every-level-crossed", true)
                ? previousLevel + 1 : lastLevel;
        for (int level = startLevel; level <= lastLevel; level++) {
            rewards.grantLevelRewards(player, level);
            if (level == lastLevel) break;
        }
    }

    private void notifyExperience(Player player, double amount, double newExperience, int level,
                                  ExperienceSource source) {
        if (player == null || !plugin.getLevelConfig().getBoolean("leveling.messages.experience.enabled", true)) return;
        String sourceKey = switch (source) {
            case PLAYER_KILL -> "player-kill";
            case PVP_DEATH -> "pvp-death";
            case LOOT -> "loot";
            case DAILY_XP -> "daily-xp";
            default -> "generic";
        };
        String message = plugin.getLevelConfig().getString("leveling.messages.experience." + sourceKey,
                "&a+%amount% XP");
        message = message.replace("%amount%", format(amount))
                .replace("%level%", String.valueOf(level))
                .replace("%exp%", format(newExperience))
                .replace("%exp_to_next_level%", format(progression.getExperienceToNextLevel(newExperience)));
        String colored = color(message);
        if (plugin.getLevelConfig().getBoolean("leveling.messages.experience.chat", true)) {
            player.sendMessage(colored);
        }
        if (plugin.getLevelConfig().getBoolean("leveling.messages.experience.action-bar", false)) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colored));
        }
        if (plugin.getLevelConfig().getBoolean("leveling.messages.experience.sound.enabled", false)) {
            String sound = plugin.getLevelConfig().getString("leveling.messages.experience.sound.sound",
                    "ENTITY_PLAYER_LEVELUP");
            float volume = (float) plugin.getLevelConfig().getDouble("leveling.messages.experience.sound.volume", 1.0);
            float pitch = (float) plugin.getLevelConfig().getDouble("leveling.messages.experience.sound.pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private ExperienceGainResult emptyResult(UUID playerId, ExperienceSource source) {
        return result(playerId, 0.0, 0.0, 0.0, getMinimumLevel(), getMinimumLevel(),
                source == null ? ExperienceSource.OTHER : source);
    }

    private ExperienceGainResult result(UUID playerId, double previous, double added, double current,
                                        int previousLevel, int newLevel, ExperienceSource source) {
        return new ExperienceGainResult(playerId, previous, added, current, previousLevel, newLevel, source);
    }

    private String format(double value) {
        return new DecimalFormat("0.0##", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }

    private String color(String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    private void refreshMenu(UUID playerId) {
        LevelMenuService menus = levelMenus;
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) player.setExp(progression.getExpBarProgress(getExperience(playerId)));
        if (menus != null && player != null) menus.refresh(player);
    }

    public void refreshPlayer(Player player) {
        if (player == null) return;
        player.setExp(progression.getExpBarProgress(getExperience(player.getUniqueId())));
        LevelMenuService menus = levelMenus;
        if (menus != null) menus.refresh(player);
    }

    private <T> T onMainThread(Callable<T> operation, T fallback) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return operation.call();
            } catch (Exception exception) {
                plugin.getLogger().severe("Could not update Pacifica Player Level data: " + exception.getMessage());
                return fallback;
            }
        }
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, operation).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException | RuntimeException exception) {
            plugin.getLogger().severe("Could not schedule Pacifica Player Level update: " + exception.getMessage());
            return fallback;
        }
    }
}