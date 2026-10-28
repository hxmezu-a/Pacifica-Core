package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class DailyExperienceRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public DailyExperienceRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public long getLastPurchase(UUID playerId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT last_purchase FROM player_level_daily_xp WHERE uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getLong("last_purchase") : 0L;
                    }
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not read Daily XP cooldown: " + exception.getMessage());
            return -1L;
        }
    }

    public boolean recordPurchase(UUID playerId, long timestamp) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_level_daily_xp (uuid, last_purchase) VALUES (?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET last_purchase = excluded.last_purchase")) {
                    statement.setString(1, playerId.toString());
                    statement.setLong(2, timestamp);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not persist Daily XP purchase: " + exception.getMessage());
            return false;
        }
    }

    public boolean reset(UUID playerId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_level_daily_xp WHERE uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    statement.executeUpdate();
                    return true;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not reset Daily XP cooldown: " + exception.getMessage());
            return false;
        }
    }
}