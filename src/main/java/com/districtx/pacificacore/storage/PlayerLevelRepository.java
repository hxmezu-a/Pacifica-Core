package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;

final class PlayerLevelRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    PlayerLevelRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    double getExperience(UUID playerId) {
        if (playerId == null) return 0.0;
        try {
            return database.player(connection -> read(connection, playerId));
        } catch (SQLException | RuntimeException exception) {
            logFailure("read Pacifica experience", exception);
            return 0.0;
        }
    }

    boolean initialize(UUID playerId) {
        if (playerId == null) return false;
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_levels (uuid, experience) VALUES (?, 0.0)")) {
                    statement.setString(1, playerId.toString());
                    statement.executeUpdate();
                }
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("initialize Pacifica level data", exception);
            return false;
        }
    }

    Change addExperience(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return Change.failed();
        return change(playerId, experience -> experience + amount);
    }

    Change removeExperience(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return Change.failed();
        return change(playerId, experience -> Math.max(0.0, experience - amount));
    }

    Change setExperience(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return Change.failed();
        return change(playerId, experience -> amount);
    }

    private Change change(UUID playerId, DoubleUnaryOperator operation) {
        if (playerId == null) return Change.failed();
        try {
            return database.player(connection -> {
                double previous = read(connection, playerId);
                double updated = operation.applyAsDouble(previous);
                if (!Double.isFinite(updated) || updated < 0.0) return Change.failed();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_levels (uuid, experience) VALUES (?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET experience = excluded.experience")) {
                    statement.setString(1, playerId.toString());
                    statement.setDouble(2, updated);
                    statement.executeUpdate();
                }
                return new Change(previous, updated, true);
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("update Pacifica experience", exception);
            return Change.failed();
        }
    }

    private double read(java.sql.Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT experience FROM player_levels WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("experience") : 0.0;
            }
        }
    }

    private void logFailure(String operation, Exception exception) {
        plugin.getLogger().severe("Could not " + operation + ": " + exception.getMessage());
    }

    static final class Change {
        private final double previous;
        private final double current;
        private final boolean successful;

        private Change(double previous, double current, boolean successful) {
            this.previous = previous;
            this.current = current;
            this.successful = successful;
        }

        static Change failed() { return new Change(0.0, 0.0, false); }
        double previous() { return previous; }
        double current() { return current; }
        boolean successful() { return successful; }
    }
}