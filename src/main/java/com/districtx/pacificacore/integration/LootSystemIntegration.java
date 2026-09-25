package com.districtx.pacificacore.integration;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.ExperienceSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Optional listener bridge for XP-bearing Pacifica-LootSystem results. */
public final class LootSystemIntegration {
    private static final String EVENT_CLASS =
            "com.districtx.pacificalootsystem.api.event.LootGeneratedEvent";
    private final PacificaCore plugin;
    private final Listener listener = new Listener() { };
    private boolean warningLogged;

    /**
     * Creates the optional LootSystem integration.
     *
     * @param plugin owning Pacifica-Core plugin
     */
    public LootSystemIntegration(PacificaCore plugin) {
        this.plugin = plugin;
    }

    /** Refreshes the optional listener after configuration reload. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void refresh() {
        disable();
        if (!plugin.getLevelConfig().getBoolean("leveling.enabled", true)
                || !plugin.getLevelConfig().getBoolean("leveling.integrations.loot-system", true)) return;
        Plugin lootSystem = plugin.getServer().getPluginManager().getPlugin("Pacifica-LootSystem");
        if (lootSystem == null || !lootSystem.isEnabled()) return;
        try {
            Class<?> eventType = lootSystem.getClass().getClassLoader().loadClass(EVENT_CLASS);
            if (!Event.class.isAssignableFrom(eventType)) return;
            Class<? extends Event> lootEventType = (Class<? extends Event>) eventType;
            plugin.getServer().getPluginManager().registerEvent(lootEventType, listener, EventPriority.MONITOR,
                    (registeredListener, event) -> awardFromLoot(event), plugin, false);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!warningLogged) {
                plugin.getLogger().warning("Could not hook Pacifica-LootSystem XP rewards: "
                        + exception.getMessage());
                warningLogged = true;
            }
        }
    }

    /** Removes the optional listener. */
    public void disable() {
        HandlerList.unregisterAll(listener);
    }

    private void awardFromLoot(Event event) {
        if (!plugin.getLevelConfig().getBoolean("leveling.enabled", true)
                || !plugin.getLevelConfig().getBoolean("leveling.integrations.loot-system", true)) return;
        try {
            Object result = event.getClass().getMethod("getResult").invoke(event);
            if (result == null) return;
            Object experience = result.getClass().getMethod("getExperience").invoke(result);
            if (!(experience instanceof Number number) || number.doubleValue() <= 0.0) return;
            Object rewardedPlayer = event.getClass().getMethod("getPlayer").invoke(event);
            if (!(rewardedPlayer instanceof Player player)) return;
            double reward = plugin.getLevelConfig().getDouble("leveling.xp-sources.loot", 1.5);
            if (Double.isFinite(reward) && reward > 0.0) {
                plugin.getAPI().getPlayerLevelService().addExperience(
                        player.getUniqueId(), reward, ExperienceSource.LOOT);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warningLogged) {
                plugin.getLogger().warning("Could not process Pacifica-LootSystem XP reward: "
                        + exception.getMessage());
                warningLogged = true;
            }
        }
    }
}