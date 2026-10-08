package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.NpcLinkService;
import com.districtx.pacificacore.api.SpawnShopNpcLinkService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SpawnShopNpcLinkManager implements SpawnShopNpcLinkService {
    private static final String FEATURE_PREFIX = "spawnshop:";

    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final NpcLinkService npcLinks;
    private final Map<String, String> npcShopLinks = new ConcurrentHashMap<>();
    private final ExecutorService writes = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Pacifica-Core-Spawn-Shop-Link-Storage");
        thread.setDaemon(true);
        return thread;
    });

    public SpawnShopNpcLinkManager(JavaPlugin plugin, DatabaseManager database, NpcLinkService npcLinks) {
        this.plugin = plugin;
        this.database = database;
        this.npcLinks = npcLinks;
        load();
    }

    @Override
    public boolean linkNpcToShop(String npcId, String shopId) {
        String npc = normalize(npcId);
        String shop = normalize(shopId);
        if (npc == null || shop == null) return false;
        npcShopLinks.put(npc, shop);
        npcLinks.link(featureId(shop), npc);
        submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO spawn_shop_npc_links (npc_id, shop_id) VALUES (?, ?) "
                            + "ON CONFLICT(npc_id) DO UPDATE SET shop_id = excluded.shop_id")) {
                statement.setString(1, npc);
                statement.setString(2, shop);
                statement.executeUpdate();
            }
            return null;
        });
        return true;
    }

    @Override
    public boolean unlinkNpc(String npcId) {
        String npc = normalize(npcId);
        if (npc == null) return false;
        String shop = npcShopLinks.remove(npc);
        if (shop == null) return false;
        String feature = featureId(shop);
        if (npcLinks.isLinkedTo(feature, npc)) {
            String replacement = npcShopLinks.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(shop))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (replacement == null) npcLinks.unlink(feature);
            else npcLinks.link(feature, replacement);
        }
        submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM spawn_shop_npc_links WHERE npc_id = ?")) {
                statement.setString(1, npc);
                statement.executeUpdate();
            }
            return null;
        });
        return true;
    }

    @Override
    public Optional<String> getLinkedShop(String npcId) {
        String npc = normalize(npcId);
        return npc == null ? Optional.empty() : Optional.ofNullable(npcShopLinks.get(npc));
    }

    @Override
    public boolean isLinked(String npcId) {
        return getLinkedShop(npcId).isPresent();
    }

    @Override
    public List<String> getNpcsLinkedToShop(String shopId) {
        String shop = normalize(shopId);
        if (shop == null) return List.of();
        List<String> linked = new ArrayList<>();
        npcShopLinks.forEach((npc, linkedShop) -> {
            if (shop.equals(linkedShop)) linked.add(npc);
        });
        linked.sort(String::compareTo);
        return List.copyOf(linked);
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
                        "SELECT npc_id, shop_id FROM spawn_shop_npc_links");
                     ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        String npc = normalize(results.getString("npc_id"));
                        String shop = normalize(results.getString("shop_id"));
                        if (npc != null && shop != null) npcShopLinks.put(npc, shop);
                    }
                }
                return null;
            });
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load Spawn Shop external NPC links: " + exception.getMessage());
        }
    }

    private void submit(DatabaseManager.SqlOperation<Void> operation) {
        writes.execute(() -> {
            try {
                database.player(operation);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not save Spawn Shop external NPC links: " + exception.getMessage());
            }
        });
    }

    private String featureId(String shopId) {
        return FEATURE_PREFIX + shopId;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}