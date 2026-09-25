package com.districtx.pacificacore.level;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.ExperienceSource;
import com.districtx.pacificacore.integration.PacificaCombatTagIntegration;
import com.districtx.pacificacore.storage.PlayerLevelManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Handles authoritative player kill, tagged death, join, and respawn progression hooks. */
public final class PlayerLevelListener implements Listener {
    private final PacificaCore plugin;
    private final PlayerLevelManager playerLevels;
    private final PacificaCombatTagIntegration combatTag;
    private final Set<UUID> rewardedDeaths = new HashSet<>();

    /**
     * Creates the progression listener.
     *
     * @param plugin owning Pacifica-Core plugin
     * @param playerLevels permanent Player Level service
     * @param combatTag optional authoritative CombatTag bridge
     */
    public PlayerLevelListener(PacificaCore plugin, PlayerLevelManager playerLevels,
                               PacificaCombatTagIntegration combatTag) {
        this.plugin = plugin;
        this.playerLevels = playerLevels;
        this.combatTag = combatTag;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getLevelConfig().getBoolean("leveling.enabled", true)) return;
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        if (!rewardedDeaths.add(victimId)) return;
        boolean taggedAtDeath = combatTag.isInCombat(victim);
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victimId)) {
            double reward = plugin.getLevelConfig().getDouble("leveling.xp-sources.player-kill", 3.0);
            playerLevels.addExperience(killer.getUniqueId(), reward, ExperienceSource.PLAYER_KILL);
        }
        if (taggedAtDeath) {
            double reward = plugin.getLevelConfig().getDouble("leveling.xp-sources.pvp-death", 1.0);
            playerLevels.addExperience(victimId, reward, ExperienceSource.PVP_DEATH);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        rewardedDeaths.remove(playerId);
        playerLevels.initializePlayer(playerId);
        playerLevels.refreshPlayer(event.getPlayer());
        playerLevels.processPendingLevelRewards(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        rewardedDeaths.remove(event.getPlayer().getUniqueId());
    }
}