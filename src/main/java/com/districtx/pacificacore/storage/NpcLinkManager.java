package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.NpcLinkService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class NpcLinkManager implements NpcLinkService {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final Map<String, String> featureLinks = new ConcurrentHashMap<>();
    private final ExecutorService writes = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Pacifica-Core-NPC-Link-Storage");
        thread.setDaemon(true);
        return thread;
    });

    public NpcLinkManager(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        load();
    }

    @Override
    public void link(String featureId, String npcId) {
        String feature = normalize(featureId);
        String npc = normalize(npcId);
        if (feature == null || npc == null) return;
        featureLinks.put(feature, npc);
        persistFeatureLink(feature, npc);
    }

    @Override
    public void unlink(String featureId) {
        String feature = normalize(featureId);
        if (feature == null) return;
        String npc = featureLinks.remove(feature);
        if (npc != null) persistFeatureUnlink(feature, npc);
    }

    @Override
    public Optional<String> getLinkedNpcId(String featureId) {
        String feature = normalize(featureId);
        return feature == null ? Optional.empty() : Optional.ofNullable(featureLinks.get(feature));
    }

    @Override
    public boolean isLinked(String featureId) {
        return getLinkedNpcId(featureId).isPresent();
    }

    @Override
    public boolean isLinkedTo(String featureId, String npcId) {
        String npc = normalize(npcId);
        return npc != null && getLinkedNpcId(featureId).filter(npc::equals).isPresent();
    }

    public void shutdown() {
        writes.shutdown();
        try {
            if (!writes.awaitTermination(3, TimeUnit.SECONDS)) writes.shutdownNow();
        } catch (InterruptedException exception) {
            writes.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void load() {
        try {
            database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT feature_id, npc_id FROM npc_links");
                     ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        String feature = normalize(results.getString("feature_id"));
                        String npc = normalize(results.getString("npc_id"));
                        if (feature != null && npc != null) featureLinks.put(feature, npc);
                    }
                }
                return null;
            });
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load external NPC links: " + exception.getMessage());
        }
    }

    private void persistFeatureLink(String feature, String npc) {
        submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO npc_links (feature_id, npc_id) VALUES (?, ?) "
                            + "ON CONFLICT(feature_id) DO UPDATE SET npc_id = excluded.npc_id")) {
                statement.setString(1, feature);
                statement.setString(2, npc);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void persistFeatureUnlink(String feature, String npc) {
        submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM npc_links WHERE feature_id = ? AND npc_id = ?")) {
                statement.setString(1, feature);
                statement.setString(2, npc);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void submit(DatabaseManager.SqlOperation<Void> operation) {
        writes.execute(() -> {
            try {
                database.player(operation);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not save external NPC links: " + exception.getMessage());
            }
        });
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}