package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PrestigeRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public PrestigeRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public int getPrestige(UUID playerId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT prestige FROM player_level_prestige WHERE uuid = ?")) {
                    statement.setString(1, playerId.toString());
                    try (ResultSet result = statement.executeQuery()) {
                        return result.next() ? result.getInt("prestige") : 0;
                    }
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not read Prestige data: " + exception.getMessage());
            return 0;
        }
    }

    public boolean setPrestige(UUID playerId, int amount) {
        if (amount < 0) return false;
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_level_prestige (uuid, prestige) VALUES (?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET prestige = excluded.prestige")) {
                    statement.setString(1, playerId.toString());
                    statement.setInt(2, amount);
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not persist Prestige data: " + exception.getMessage());
            return false;
        }
    }
}