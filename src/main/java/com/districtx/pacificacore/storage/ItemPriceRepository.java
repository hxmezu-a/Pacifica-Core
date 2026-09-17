package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ItemPriceRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public ItemPriceRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public BigDecimal getPrice(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) return BigDecimal.ZERO;
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT price FROM item_prices WHERE item_key = ?")) {
                    statement.setString(1, itemKey);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next() ? new BigDecimal(resultSet.getString("price")) : BigDecimal.ZERO;
                    }
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not read item price: " + exception.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public boolean setPrice(String itemKey, BigDecimal price) {
        if (itemKey == null || itemKey.isBlank() || price == null || price.signum() < 0) return false;
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO item_prices (item_key, price) VALUES (?, ?) "
                                + "ON CONFLICT(item_key) DO UPDATE SET price = excluded.price")) {
                    statement.setString(1, itemKey);
                    statement.setString(2, price.toPlainString());
                    statement.executeUpdate();
                }
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not save item price: " + exception.getMessage());
            return false;
        }
    }

    public boolean removePrice(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) return false;
        try {
            return database.itemPrices(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM item_prices WHERE item_key = ?")) {
                    statement.setString(1, itemKey);
                    statement.executeUpdate();
                }
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not remove item price: " + exception.getMessage());
            return false;
        }
    }
}