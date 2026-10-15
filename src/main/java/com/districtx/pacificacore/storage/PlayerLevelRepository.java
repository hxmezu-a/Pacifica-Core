package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.UnaryOperator;

final class PlayerLevelRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    PlayerLevelRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    double getExperience(UUID playerId) {
        return getExactExperience(playerId).doubleValue();
    }

    BigDecimal getExactExperience(UUID playerId) {
        if (playerId == null) return BigDecimal.ZERO;
        try {
            return database.player(connection -> read(connection, playerId));
        } catch (SQLException | RuntimeException exception) {
            logFailure("read Pacifica experience", exception);
            return BigDecimal.ZERO;
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
        return !Double.isFinite(amount) || amount <= 0.0
                ? Change.failed() : addExperience(playerId, BigDecimal.valueOf(amount));
    }

    Change addExperience(UUID playerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return Change.failed();
        return change(playerId, experience -> experience.add(amount));
    }

    Change removeExperience(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return Change.failed();
        BigDecimal removal = BigDecimal.valueOf(amount);
        return change(playerId, experience -> experience.subtract(removal).max(BigDecimal.ZERO));
    }

    Change setExperience(UUID playerId, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) return Change.failed();
        return setExperience(playerId, BigDecimal.valueOf(amount));
    }

    Change setExperience(UUID playerId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) return Change.failed();
        return change(playerId, experience -> amount);
    }

    private Change change(UUID playerId, UnaryOperator<BigDecimal> operation) {
        if (playerId == null) return Change.failed();
        try {
            return database.player(connection -> {
                BigDecimal previous = read(connection, playerId);
                BigDecimal updated = operation.apply(previous);
                if (updated == null || updated.signum() < 0) return Change.failed();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_levels (uuid, experience) VALUES (?, ?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET experience = excluded.experience")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, updated.toString());
                    statement.executeUpdate();
                }
                return new Change(previous, updated, true);
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("update Pacifica experience", exception);
            return Change.failed();
        }
    }

    private BigDecimal read(java.sql.Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT experience FROM player_levels WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return BigDecimal.ZERO;
                String value = resultSet.getString("experience");
                return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
            }
        }
    }

    private void logFailure(String operation, Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "Could not " + operation + ".", exception);
    }

    static final class Change {
        private final BigDecimal previous;
        private final BigDecimal current;
        private final boolean successful;

        private Change(BigDecimal previous, BigDecimal current, boolean successful) {
            this.previous = previous;
            this.current = current;
            this.successful = successful;
        }

        static Change failed() { return new Change(BigDecimal.ZERO, BigDecimal.ZERO, false); }
        BigDecimal previous() { return previous; }
        BigDecimal current() { return current; }
        boolean successful() { return successful; }
    }
}