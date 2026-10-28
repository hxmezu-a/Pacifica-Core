package com.districtx.pacificacore.storage;

import com.districtx.pacificacore.api.SpawnShopItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SpawnShopRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public SpawnShopRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public List<SpawnShopItem> findAll() {
        try {
            return database.spawnShop(connection -> {
                List<SpawnShopItem> items = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM spawn_shop_items");
                     ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        try {
                            items.add(new SpawnShopItem(UUID.fromString(results.getString("item_id")),
                                    results.getString("shop_id"), results.getString("category"),
                                    deserialize(results.getString("item_stack")),
                                    results.getDouble("unit_price"), results.getInt("slot")));
                        } catch (IOException | ClassNotFoundException | IllegalArgumentException exception) {
                            plugin.getLogger().warning("Skipping invalid Spawn Shop item "
                                    + results.getString("item_id") + ": " + exception.getMessage());
                        }
                    }
                }
                return items;
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not load Spawn Shop items: " + exception.getMessage());
            return List.of();
        }
    }

    public Optional<SpawnShopItem> find(UUID itemId) {
        return findAll().stream().filter(item -> item.id().equals(itemId)).findFirst();
    }

    public boolean save(SpawnShopItem item) {
        try {
            String itemData = serialize(item.itemStack());
            return database.spawnShop(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO spawn_shop_items (item_id, shop_id, category, slot, item_stack, unit_price) "
                                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(item_id) DO UPDATE SET "
                                + "shop_id = excluded.shop_id, category = excluded.category, slot = excluded.slot, "
                                + "item_stack = excluded.item_stack, unit_price = excluded.unit_price")) {
                    statement.setString(1, item.id().toString());
                    statement.setString(2, item.shopId());
                    statement.setString(3, item.category());
                    statement.setInt(4, item.slot());
                    statement.setString(5, itemData);
                    statement.setDouble(6, item.unitPrice());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | IOException | RuntimeException exception) {
            plugin.getLogger().severe("Could not save Spawn Shop item " + item.id() + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean delete(UUID itemId) {
        try {
            return database.spawnShop(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM spawn_shop_items WHERE item_id = ?")) {
                    statement.setString(1, itemId.toString());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not remove Spawn Shop item " + itemId + ": " + exception.getMessage());
            return false;
        }
    }

    private String serialize(ItemStack item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private ItemStack deserialize(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            if (!(value instanceof ItemStack item)) throw new IOException("Stored value is not an ItemStack.");
            return item;
        }
    }
}