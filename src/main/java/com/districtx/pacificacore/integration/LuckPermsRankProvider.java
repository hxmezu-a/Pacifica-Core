package com.districtx.pacificacore.integration;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.RankProvider;
import com.districtx.pacificacore.api.ExperienceSource;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/** Reflection-isolated LuckPerms adapter so LuckPerms remains a true soft dependency. */
public final class LuckPermsRankProvider implements RankProvider {
    private final PacificaCore plugin;

    public LuckPermsRankProvider(PacificaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isAvailable() {
        org.bukkit.plugin.Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        return luckPerms != null && luckPerms.isEnabled();
    }

    @Override
    public String getPrimaryGroup(UUID playerId) {
        if (playerId == null || !isAvailable()) return "default";
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Class<?> luckPermsType = Class.forName("net.luckperms.api.LuckPerms");
            Object userManager = luckPermsType.getMethod("getUserManager").invoke(api);
            Class<?> userManagerType = Class.forName("net.luckperms.api.model.user.UserManager");
            Object user = userManagerType.getMethod("getUser", UUID.class).invoke(userManager, playerId);
            if (user == null) return "default";
            Class<?> userType = Class.forName("net.luckperms.api.model.user.User");
            Object groupValue = userType.getMethod("getPrimaryGroup").invoke(user);
            if (groupValue instanceof String group && !group.isBlank()) return group;
            Class<?> holderType = Class.forName("net.luckperms.api.model.PermissionHolder");
            Object cachedData = holderType.getMethod("getCachedData").invoke(user);
            Class<?> cachedDataType = Class.forName("net.luckperms.api.cacheddata.CachedDataManager");
            Object metaData = cachedDataType.getMethod("getMetaData").invoke(cachedData);
            Class<?> metaDataType = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Object primaryGroup = metaDataType.getMethod("getPrimaryGroup").invoke(metaData);
            return primaryGroup instanceof String group && !group.isBlank() ? group : "default";
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("LuckPerms rank lookup is unavailable: " + exception.getMessage());
            return "default";
        }
    }

    @Override
    public double getXpBonus(UUID playerId, ExperienceSource source) {
        if (!plugin.getLevelConfig().getBoolean("rank-bonuses.enabled", true)) return 0.0;
        ExperienceSource effectiveSource = source == null ? ExperienceSource.OTHER : source;
        String key = switch (effectiveSource) {
            case PLAYER_KILL -> "kill";
            case PVP_DEATH -> "death";
            case LOOT -> "loot";
            case DAILY_XP -> "daily-xp";
            default -> "other";
        };
        String rank = getPrimaryGroup(playerId).toLowerCase(java.util.Locale.ROOT);
        return plugin.getLevelConfig().getDouble("rank-bonuses.ranks." + rank + "." + key,
                plugin.getLevelConfig().getDouble("rank-bonuses.default." + key, 0.0));
    }
}