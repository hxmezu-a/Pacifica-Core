package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class BankingRepository {
    private final JavaPlugin plugin;
    private final DatabaseManager database;

    public BankingRepository(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public BigDecimal getBalance(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId cannot be null");
        try {
            return database.currency(connection -> readBankBalance(connection, playerId));
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not read a bank account: " + exception.getMessage());
            throw new IllegalStateException("Could not read the bank account.", exception);
        }
    }

    BalanceUpdate depositFromBalance(UUID playerId, BigDecimal amount) {
        return transact(() -> database.currency(connection -> {
            BigDecimal serverBalance = readServerBalance(connection, playerId);
            BigDecimal bankBalance = readBankBalance(connection, playerId);
            if (serverBalance.compareTo(amount) < 0) return null;
            BigDecimal updatedServerBalance = serverBalance.subtract(amount);
            BigDecimal updatedBankBalance = bankBalance.add(amount);
            if (!supported(updatedServerBalance) || !supported(updatedBankBalance)) return null;
            writeServerBalance(connection, playerId, updatedServerBalance);
            writeBankBalance(connection, playerId, updatedBankBalance);
            record(connection, "DEPOSIT", playerId, null, amount);
            return new BalanceUpdate(updatedServerBalance, updatedBankBalance);
        }), "deposit");
    }

    BalanceUpdate withdrawToBalance(UUID playerId, BigDecimal amount) {
        return transact(() -> database.currency(connection -> {
            BigDecimal bankBalance = readBankBalance(connection, playerId);
            BigDecimal serverBalance = readServerBalance(connection, playerId);
            if (bankBalance.compareTo(amount) < 0) return null;
            BigDecimal updatedBankBalance = bankBalance.subtract(amount);
            BigDecimal updatedServerBalance = serverBalance.add(amount);
            if (!supported(updatedBankBalance) || !supported(updatedServerBalance)) return null;
            writeBankBalance(connection, playerId, updatedBankBalance);
            writeServerBalance(connection, playerId, updatedServerBalance);
            record(connection, "WITHDRAW", playerId, null, amount);
            return new BalanceUpdate(updatedServerBalance, updatedBankBalance);
        }), "withdraw");
    }

    boolean transfer(UUID senderId, UUID recipientId, BigDecimal amount) {
        Boolean result = transact(() -> database.currency(connection -> {
            BigDecimal senderBalance = readBankBalance(connection, senderId);
            BigDecimal recipientBalance = readBankBalance(connection, recipientId);
            if (senderBalance.compareTo(amount) < 0) return false;
            BigDecimal updatedSenderBalance = senderBalance.subtract(amount);
            BigDecimal updatedRecipientBalance = recipientBalance.add(amount);
            if (!supported(updatedSenderBalance) || !supported(updatedRecipientBalance)) return false;
            writeBankBalance(connection, senderId, updatedSenderBalance);
            writeBankBalance(connection, recipientId, updatedRecipientBalance);
            record(connection, "TRANSFER", senderId, recipientId, amount);
            return true;
        }), "transfer");
        return Boolean.TRUE.equals(result);
    }

    private <T> T transact(SqlCall<T> call, String name) {
        try {
            return call.execute();
        } catch (SQLException | RuntimeException exception) {
            plugin.getLogger().severe("Could not complete bank " + name + ": " + exception.getMessage());
            throw new IllegalStateException("Bank " + name + " failed.", exception);
        }
    }

    private BigDecimal readBankBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT bank_balance FROM bank_accounts WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new BigDecimal(result.getString("bank_balance")) : BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal readServerBalance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM economy_balances WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new BigDecimal(result.getString("balance")) : BigDecimal.ZERO;
            }
        }
    }

    private void writeBankBalance(Connection connection, UUID playerId, BigDecimal amount) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bank_accounts (player_uuid, bank_balance, created_at, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(player_uuid) DO UPDATE SET bank_balance = excluded.bank_balance, "
                        + "updated_at = excluded.updated_at")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, amount.toPlainString());
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private void writeServerBalance(Connection connection, UUID playerId, BigDecimal amount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE economy_balances SET balance = ? WHERE uuid = ?")) {
            update.setString(1, amount.toPlainString());
            update.setString(2, playerId.toString());
            if (update.executeUpdate() > 0) return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO economy_balances (uuid, player_name, balance) VALUES (?, ?, ?)")) {
            insert.setString(1, playerId.toString());
            insert.setString(2, playerId.toString());
            insert.setString(3, amount.toPlainString());
            insert.executeUpdate();
        }
    }

    private void record(Connection connection, String type, UUID source, UUID recipient,
                        BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bank_transactions (transaction_id, transaction_type, source_uuid, recipient_uuid, "
                        + "amount, created_at, status) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, type);
            statement.setString(3, source.toString());
            statement.setString(4, recipient == null ? null : recipient.toString());
            statement.setString(5, amount.toPlainString());
            statement.setLong(6, System.currentTimeMillis());
            statement.setString(7, "SUCCESS");
            statement.executeUpdate();
        }
    }

    private boolean supported(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() <= 8 && normalized.precision() - normalized.scale() <= 38;
    }

    record BalanceUpdate(BigDecimal serverBalance, BigDecimal bankBalance) {
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T execute() throws SQLException;
    }
}