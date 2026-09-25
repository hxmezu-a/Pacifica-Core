package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Persists level ranges awaiting reward processing after offline progression or an interrupted grant. */
public final class PendingLevelRewardRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public PendingLevelRewardRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean record(UUID playerId, int previousLevel, int newLevel) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_level_pending_rewards (uuid, previous_level, new_level) VALUES (?, ?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET previous_level = MIN(previous_level, excluded.previous_level), "
                                + "new_level = MAX(new_level, excluded.new_level)")) {
                    statement.setString(1, playerId.toString());
                    statement.setInt(2, previousLevel);
                    statement.setInt(3, newLevel);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not persist pending level rewards: " + exception.getMessage());
            return false;
        }
    }

    public Range get(UUID playerId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT previous_level, new_level FROM player_level_pending_rewards WHERE uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? new Range(result.getInt("previous_level"), result.getInt("new_level")) : null;
                    }
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not read pending level rewards: " + exception.getMessage());
            return null;
        }
    }

    public boolean clear(UUID playerId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_level_pending_rewards WHERE uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    statement.executeUpdate();
                    return true;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not clear pending level rewards: " + exception.getMessage());
            return false;
        }
    }

    public record Range(int previousLevel, int newLevel) { }
}