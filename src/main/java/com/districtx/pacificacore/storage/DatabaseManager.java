package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager implements AutoCloseable {
    @FunctionalInterface
    interface SqlOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final JavaPlugin plugin;
    private Connection playerConnection;
    private Connection itemPriceConnection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() throws SQLException {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new SQLException("Could not create the plugin data directory.");
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is not available.", exception);
        }
        playerConnection = open(new File(plugin.getDataFolder(), "player.db"));
        itemPriceConnection = open(new File(plugin.getDataFolder(), "itemprices.db"));
        createPlayerSchema();
        createItemPriceSchema();
    }

    public synchronized <T> T player(SqlOperation<T> operation) throws SQLException {
        return transaction(playerConnection, operation);
    }

    public synchronized <T> T itemPrices(SqlOperation<T> operation) throws SQLException {
        return transaction(itemPriceConnection, operation);
    }

    private Connection open(File file) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private void createPlayerSchema() throws SQLException {
        player(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_balances ("
                        + "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, "
                        + "balance TEXT NOT NULL DEFAULT '0')");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS economy_balances ("
                        + "uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, "
                        + "balance TEXT NOT NULL DEFAULT '0')");
            }
            return null;
        });
    }

    private void createItemPriceSchema() throws SQLException {
        itemPrices(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS item_prices ("
                        + "item_key TEXT PRIMARY KEY, price TEXT NOT NULL)");
            }
            return null;
        });
    }

    private <T> T transaction(Connection connection, SqlOperation<T> operation) throws SQLException {
        if (connection == null || connection.isClosed()) throw new SQLException("Database connection is closed.");
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = operation.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            if (exception instanceof SQLException sqlException) throw sqlException;
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public synchronized void close() {
        close(playerConnection);
        close(itemPriceConnection);
        playerConnection = null;
        itemPriceConnection = null;
    }

    private void close(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close a Pacifica-Core database connection: "
                    + exception.getMessage());
        }
    }
}