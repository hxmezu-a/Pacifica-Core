package com.districtx.pacificacore.level;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.AdvancementNotificationService;
import com.districtx.pacificacore.api.ExperienceGainResult;
import com.districtx.pacificacore.api.event.PlayerExperienceGrantedEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

public final class AdvancementNotificationManager implements AdvancementNotificationService, Listener {
    private static final String CRITERION = "experience_gain";
    private static final int MAX_REMEMBERED_TRANSACTIONS = 4096;

    private final PacificaCore plugin;
    private final Set<UUID> notifiedTransactions = new LinkedHashSet<>();
    private final Set<UUID> deferredNotificationPlayers = new HashSet<>();
    private final Map<UUID, List<ExperienceGainResult>> pendingNotifications = new HashMap<>();
    private Advancement advancement;
    private NamespacedKey advancementKey;

    public AdvancementNotificationManager(PacificaCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (!isToastEnabled()) {
            advancement = null;
            advancementKey = null;
            return;
        }
        ensureAdvancement();
    }

    @Override
    public boolean isEnabled() {
        return isToastEnabled();
    }

    @EventHandler
    public void onExperienceGranted(PlayerExperienceGrantedEvent event) {
        ExperienceGainResult result = event.getResult();
        if (result == null || result.getPlayerId() == null) return;
        Player player = Bukkit.getPlayer(result.getPlayerId());
        if (player != null) notifyExperienceGain(player, result);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        deferredNotificationPlayers.add(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refresh(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @Override
    public void notifyExperienceGain(Player player, ExperienceGainResult result) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> notifyExperienceGain(player, result));
            return;
        }
        if (player == null || result == null || !player.isOnline()
                || !player.getUniqueId().equals(result.getPlayerId())
                || result.getTransactionId() == null || result.getExperienceAdded() <= 0.0
                || !isEnabled()) return;
        if (player.isDead() || deferredNotificationPlayers.contains(player.getUniqueId())) {
            pendingNotifications.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(result);
            return;
        }
        UUID transactionId = result.getTransactionId();
        if (!notifiedTransactions.add(transactionId)) return;
        if (notifiedTransactions.size() > MAX_REMEMBERED_TRANSACTIONS) {
            Iterator<UUID> iterator = notifiedTransactions.iterator();
            iterator.next();
            iterator.remove();
        }

        boolean toastShown = isToastEnabled() && showToast(player);
        if (plugin.getLevelConfig().getBoolean("leveling.debug.advancements", false)
                || plugin.getLevelConfig().getBoolean("debug.advancement-notification", false)) {
            plugin.getLogger().info("XP transaction " + transactionId + ": player=" + result.getPlayerId()
                    + " source=" + result.getSource() + " amount="
                    + ExperienceFormatter.formatReward(result.getExperienceAdded())
                    + " advancement=" + (toastShown ? "shown" : isToastEnabled() ? "failed" : "disabled"));
        }
    }

    @Override
    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        deferredNotificationPlayers.remove(player.getUniqueId());
        List<ExperienceGainResult> pending = pendingNotifications.remove(player.getUniqueId());
        if (pending == null) return;
        for (ExperienceGainResult result : pending) notifyExperienceGain(player, result);
    }

    @Override
    public void cleanup(Player player) {
        if (player == null) return;
        pendingNotifications.remove(player.getUniqueId());
        deferredNotificationPlayers.remove(player.getUniqueId());
    }

    private boolean isToastEnabled() {
        return plugin.getLevelConfig().getBoolean("leveling.messages.experience.advancement.enabled", true)
                && plugin.getLevelConfig().getBoolean("leveling.messages.experience.advancement.toast", true);
    }

    private boolean showToast(Player player) {
        ensureAdvancement();
        if (advancement == null) return false;
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String awarded : progress.getAwardedCriteria()) progress.revokeCriteria(awarded);
        if (!progress.awardCriteria(CRITERION)) return false;
        progress.revokeCriteria(CRITERION);
        return true;
    }

    private void ensureAdvancement() {
        String definition = createAdvancementJson();
        String fingerprint = UUID.nameUUIDFromBytes(definition.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        NamespacedKey key = new NamespacedKey(plugin, "experience_reward_" + fingerprint);
        if (key.equals(advancementKey) && advancement != null) return;

        Advancement loaded = Bukkit.getAdvancement(key);
        if (loaded == null) loaded = loadAdvancement(key, definition);
        advancementKey = key;
        advancement = loaded;
    }

    @SuppressWarnings("deprecation")
    private Advancement loadAdvancement(NamespacedKey key, String definition) {
        try {
            Advancement loaded = Bukkit.getUnsafe().loadAdvancement(key, definition);
            if (loaded == null) plugin.getLogger().warning("Could not load the XP reward advancement.");
            return loaded;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Could not load the XP reward advancement: " + exception.getMessage());
            return null;
        }
    }

    private String createAdvancementJson() {
        String icon = resourceLocation(plugin.getLevelConfig().getString(
                "leveling.messages.experience.advancement.icon", "EXPERIENCE_BOTTLE"), "minecraft:experience_bottle");
        String frame = plugin.getLevelConfig().getString(
                "leveling.messages.experience.advancement.frame", "TASK").toLowerCase(Locale.ROOT);
        if (!frame.equals("task") && !frame.equals("goal") && !frame.equals("challenge")) frame = "task";
        String title = componentJson(plugin.getLevelConfig().getString(
                "leveling.messages.experience.advancement.title", "&a&lExperience Gained"));
        String description = componentJson(plugin.getLevelConfig().getString(
                "leveling.messages.experience.advancement.description", "&7You gained Player Level experience."));
        String background = plugin.getLevelConfig().getString(
                "leveling.messages.experience.advancement.background", "").trim();

        StringBuilder json = new StringBuilder("{\"display\":{\"icon\":{\"id\":\"")
                .append(icon).append("\"},\"title\":").append(title)
                .append(",\"description\":").append(description)
                .append(",\"frame\":\"").append(frame)
                .append("\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true");
        if (!background.isEmpty()) {
            String resource = resourceLocation(background, "");
            if (!resource.isEmpty()) json.append(",\"background\":\"").append(resource).append('"');
        }
        return json.append("},\"criteria\":{\"").append(CRITERION)
                .append("\":{\"trigger\":\"minecraft:impossible\"}}}").toString();
    }

    private String componentJson(String text) {
        return GsonComponentSerializer.gson().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }

    private String resourceLocation(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        return normalized.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ? normalized : fallback;
    }
}