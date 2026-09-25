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
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_levels ("
                        + "uuid TEXT PRIMARY KEY, experience REAL NOT NULL DEFAULT 0.0)");
            }
            return null;
        });
    }

    private void createItemPriceSchema() throws SQLException {
        itemPrices(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS item_prices ("
                        + "item_key TEXT PRIMARY KEY, price TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS black_market_offers ("
                        + "offer_id TEXT PRIMARY KEY, seller_uuid TEXT NOT NULL, category TEXT NOT NULL, "
                        + "offer_type TEXT NOT NULL, item_data TEXT NOT NULL, original_quantity INTEGER NOT NULL, "
                        + "remaining_quantity INTEGER NOT NULL, price TEXT NOT NULL, created_at INTEGER NOT NULL, "
                        + "expires_at INTEGER NOT NULL, status TEXT NOT NULL, fulfilled_quantity INTEGER NOT NULL DEFAULT 0, "
                        + "reserved_funds TEXT NOT NULL DEFAULT '0', minimum_bid TEXT NOT NULL DEFAULT '0', "
                        + "highest_bidder_uuid TEXT, claimed_quantity INTEGER NOT NULL DEFAULT 0, "
                        + "logical_offer_slot INTEGER)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS black_market_bids ("
                        + "bid_id TEXT PRIMARY KEY, offer_id TEXT NOT NULL, bidder_uuid TEXT NOT NULL, "
                        + "amount TEXT NOT NULL, created_at INTEGER NOT NULL, refunded INTEGER NOT NULL DEFAULT 0, "
                        + "FOREIGN KEY (offer_id) REFERENCES black_market_offers(offer_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS black_market_buy_escrow ("
                        + "offer_id TEXT PRIMARY KEY, item_data TEXT NOT NULL, quantity INTEGER NOT NULL, "
                        + "FOREIGN KEY (offer_id) REFERENCES black_market_offers(offer_id))");
                addColumn(statement, "black_market_offers", "fulfilled_quantity INTEGER NOT NULL DEFAULT 0");
                addColumn(statement, "black_market_offers", "reserved_funds TEXT NOT NULL DEFAULT '0'");
                addColumn(statement, "black_market_offers", "minimum_bid TEXT NOT NULL DEFAULT '0'");
                addColumn(statement, "black_market_offers", "highest_bidder_uuid TEXT");
                addColumn(statement, "black_market_offers", "claimed_quantity INTEGER NOT NULL DEFAULT 0");
                addColumn(statement, "black_market_offers", "logical_offer_slot INTEGER");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_black_market_active "
                        + "ON black_market_offers(status, category, expires_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_black_market_bids_offer "
                        + "ON black_market_bids(offer_id, created_at)");
            }
            return null;
        });
    }

    private void addColumn(Statement statement, String table, String definition) {
        try {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + definition);
        } catch (SQLException ignored) {
        }
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