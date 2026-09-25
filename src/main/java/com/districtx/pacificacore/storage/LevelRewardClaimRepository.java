package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class LevelRewardClaimRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public LevelRewardClaimRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean claim(UUID playerId, String rewardId) {
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_level_reward_claims (uuid, reward_id, claimed_at) VALUES (?, ?, ?)")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, rewardId);
                    statement.setLong(3, System.currentTimeMillis());
                    return statement.executeUpdate() == 1;
                }
            });
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not persist level reward claim " + rewardId + ": " + exception.getMessage());
            return false;
        }
    }
}