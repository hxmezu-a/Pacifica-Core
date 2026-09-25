package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerBalanceRepository {
    private static final String DIAMOND_TABLE = "player_balances";
    private static final String ECONOMY_TABLE = "economy_balances";

    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public PlayerBalanceRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public Map<UUID, BigDecimal> loadDiamonds() {
        return load(DIAMOND_TABLE);
    }

    public Map<UUID, BigDecimal> loadBalances() {
        return load(ECONOMY_TABLE);
    }

    public BigDecimal getDiamond(UUID player) {
        return get(DIAMOND_TABLE, player);
    }

    public BigDecimal getBalance(UUID player) {
        return get(ECONOMY_TABLE, player);
    }

    public boolean setDiamond(UUID player, BigDecimal amount) {
        return set(DIAMOND_TABLE, player, amount);
    }

    public boolean setBalance(UUID player, BigDecimal amount) {
        return set(ECONOMY_TABLE, player, amount);
    }

    public boolean importDiamondIfAbsent(UUID player, BigDecimal amount) {
        return importIfAbsent(DIAMOND_TABLE, player, amount);
    }

    public boolean importBalanceIfAbsent(UUID player, BigDecimal amount) {
        return importIfAbsent(ECONOMY_TABLE, player, amount);
    }

    public boolean transfer(UUID from, UUID to, BigDecimal diamondAmount, BigDecimal balanceAmount) {
        if (from == null || to == null) return false;
        try {
            return database.player(connection -> {
                BigDecimal fromDiamonds = read(connection, DIAMOND_TABLE, from);
                BigDecimal fromBalance = read(connection, ECONOMY_TABLE, from);
                if (fromDiamonds.compareTo(diamondAmount) < 0 || fromBalance.compareTo(balanceAmount) < 0) {
                    return false;
                }
                if (from.equals(to)) return true;
                BigDecimal toDiamonds = read(connection, DIAMOND_TABLE, to);
                BigDecimal toBalance = read(connection, ECONOMY_TABLE, to);
                write(connection, DIAMOND_TABLE, from, fromDiamonds.subtract(diamondAmount));
                write(connection, DIAMOND_TABLE, to, toDiamonds.add(diamondAmount));
                write(connection, ECONOMY_TABLE, from, fromBalance.subtract(balanceAmount));
                write(connection, ECONOMY_TABLE, to, toBalance.add(balanceAmount));
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("transfer balances", exception);
            return false;
        }
    }

    public boolean setBoth(UUID player, BigDecimal diamondAmount, BigDecimal balanceAmount) {
        if (player == null) return false;
        try {
            return database.player(connection -> {
                write(connection, DIAMOND_TABLE, player, diamondAmount);
                write(connection, ECONOMY_TABLE, player, balanceAmount);
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("set both balances", exception);
            return false;
        }
    }

    private Map<UUID, BigDecimal> load(String table) {
        try {
            return database.player(connection -> {
                Map<UUID, BigDecimal> result = new HashMap<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT uuid, balance FROM " + table);
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        try {
                            UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                            BigDecimal amount = new BigDecimal(resultSet.getString("balance"));
                            if (amount.signum() >= 0) result.put(uuid, amount);
                        } catch (IllegalArgumentException exception) {
                            plugin.getLogger().warning("Ignoring invalid balance row in " + table + ".");
                        }
                    }
                }
                return result;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("load " + table, exception);
            return new HashMap<>();
        }
    }

    private BigDecimal get(String table, UUID player) {
        if (player == null) return BigDecimal.ZERO;
        try {
            return database.player(connection -> read(connection, table, player));
        } catch (SQLException | RuntimeException exception) {
            logFailure("read balance", exception);
            return BigDecimal.ZERO;
        }
    }

    private boolean set(String table, UUID player, BigDecimal amount) {
        if (player == null || amount == null) return false;
        try {
            return database.player(connection -> {
                write(connection, table, player, amount);
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("write balance", exception);
            return false;
        }
    }

    private boolean importIfAbsent(String table, UUID player, BigDecimal amount) {
        if (player == null || amount == null) return false;
        try {
            return database.player(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM " + table + " WHERE uuid = ?")) {
                    statement.setString(1, player.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) return true;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + table + " (uuid, player_name, balance) VALUES (?, ?, ?)")) {
                    statement.setString(1, player.toString());
                    statement.setString(2, player.toString());
                    statement.setString(3, amount.toPlainString());
                    statement.executeUpdate();
                }
                return true;
            });
        } catch (SQLException | RuntimeException exception) {
            logFailure("migrate balance", exception);
            return false;
        }
    }

    private BigDecimal read(java.sql.Connection connection, String table, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? new BigDecimal(resultSet.getString("balance")) : BigDecimal.ZERO;
            }
        }
    }

    private void write(java.sql.Connection connection, String table, UUID player, BigDecimal amount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET balance = ? WHERE uuid = ?")) {
            update.setString(1, amount.toPlainString());
            update.setString(2, player.toString());
            if (update.executeUpdate() > 0) return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + " (uuid, player_name, balance) VALUES (?, ?, ?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, player.toString());
            insert.setString(3, amount.toPlainString());
            insert.executeUpdate();
        }
    }

    private void logFailure(String operation, Exception exception) {
        plugin.getLogger().severe("Could not " + operation + ": " + exception.getMessage());
    }
}