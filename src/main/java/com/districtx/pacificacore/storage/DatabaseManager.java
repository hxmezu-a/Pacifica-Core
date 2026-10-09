package com.districtx.pacificacore.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseManager implements AutoCloseable {
    @FunctionalInterface
    interface SqlOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final JavaPlugin plugin;
    private Connection levelConnection;
    private Connection currencyConnection;
    private Connection spawnShopConnection;
    private Connection blackMarketConnection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) throw new SQLException("Could not create the plugin data directory.");
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is not available.", exception);
        }
        File legacyPlayer = new File(dataFolder, "player.db");
        File legacyItemPrices = new File(dataFolder, "itemprices.db");
        backupLegacyDatabase(legacyPlayer, new File(dataFolder, "level/.migration-backups/player.db.bak"));
        backupLegacyDatabase(legacyItemPrices, new File(dataFolder, "BlackMarket/.migration-backups/itemprices.db.bak"));

        levelConnection = open(new File(dataFolder, "level/player.db"));
        currencyConnection = open(new File(dataFolder, "Currency/balance.db"));
        spawnShopConnection = open(new File(dataFolder, "SpawnShop/shop.db"));
        blackMarketConnection = open(new File(dataFolder, "BlackMarket/itemprices.db"));
        createLevelSchema();
        createCurrencySchema();
        createSpawnShopSchema();
        createBlackMarketSchema();

        importPlayerData(legacyPlayer);
        importItemPriceData(legacyItemPrices);
        retireLegacyDatabase(legacyPlayer);
        retireLegacyDatabase(legacyItemPrices);
    }

    public synchronized <T> T player(SqlOperation<T> operation) throws SQLException {
        return transaction(levelConnection, operation);
    }

    public synchronized <T> T currency(SqlOperation<T> operation) throws SQLException {
        return transaction(currencyConnection, operation);
    }

    public synchronized <T> T spawnShop(SqlOperation<T> operation) throws SQLException {
        return transaction(spawnShopConnection, operation);
    }

    public synchronized <T> T blackMarket(SqlOperation<T> operation) throws SQLException {
        return transaction(blackMarketConnection, operation);
    }

    public synchronized <T> T itemPrices(SqlOperation<T> operation) throws SQLException {
        return transaction(blackMarketConnection, operation);
    }

    private Connection open(File file) throws SQLException {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new SQLException("Could not create database directory " + parent + ".");
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=DELETE");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private void createLevelSchema() throws SQLException {
        player(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_levels ("
                        + "uuid TEXT PRIMARY KEY, experience REAL NOT NULL DEFAULT 0.0)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_level_reward_claims ("
                        + "uuid TEXT NOT NULL, reward_id TEXT NOT NULL, claimed_at INTEGER NOT NULL, "
                        + "PRIMARY KEY (uuid, reward_id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_level_daily_xp ("
                        + "uuid TEXT PRIMARY KEY, last_purchase INTEGER NOT NULL DEFAULT 0)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_level_prestige ("
                        + "uuid TEXT PRIMARY KEY, prestige INTEGER NOT NULL DEFAULT 0)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_level_pending_rewards ("
                        + "uuid TEXT PRIMARY KEY, previous_level INTEGER NOT NULL, new_level INTEGER NOT NULL)");
            }
            return null;
        });
    }

    private void createCurrencySchema() throws SQLException {
        currency(connection -> {
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

    private void createSpawnShopSchema() throws SQLException {
        spawnShop(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS npc_links ("
                        + "feature_id TEXT PRIMARY KEY, npc_id TEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS spawn_shop_npc_links ("
                        + "npc_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_spawn_shop_npc_links_shop "
                        + "ON spawn_shop_npc_links(shop_id)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS spawn_shop_items ("
                        + "item_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, category TEXT, slot INTEGER NOT NULL, "
                        + "item_stack TEXT NOT NULL, unit_price REAL NOT NULL)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_spawn_shop_items "
                        + "ON spawn_shop_items(shop_id, category, slot)");
            }
            return null;
        });
    }

    private void createBlackMarketSchema() throws SQLException {
        blackMarket(connection -> {
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

    private void backupLegacyDatabase(File source, File backup) throws SQLException {
        if (!source.isFile()) {
            for (String suffix : List.of("-wal", "-shm", "-journal")) {
                if (new File(source.getPath() + suffix).isFile()) {
                    throw new SQLException("Found a SQLite sidecar without its database file: " + source.getName() + suffix);
                }
            }
            return;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            try (ResultSet result = statement.executeQuery("PRAGMA wal_checkpoint(FULL)")) {
                if (result.next() && result.getInt(1) != 0) throw new SQLException("Could not checkpoint " + source.getName());
            }
            try (ResultSet result = statement.executeQuery("PRAGMA journal_mode=DELETE")) {
                if (result.next() && !"delete".equalsIgnoreCase(result.getString(1))) {
                    throw new SQLException("Could not checkpoint " + source.getName() + " to a single-file journal.");
                }
            }
        } catch (SQLException exception) {
            throw new SQLException("Could not safely prepare legacy database " + source.getName() + ".", exception);
        }
        try {
            Files.createDirectories(backup.toPath().getParent());
            if (!backup.exists()) Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            for (String suffix : List.of("-wal", "-shm", "-journal")) {
                File sidecar = new File(source.getPath() + suffix);
                File sidecarBackup = new File(backup.getPath() + suffix);
                if (sidecar.isFile() && !sidecarBackup.exists()) {
                    Files.copy(sidecar.toPath(), sidecarBackup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException exception) {
            throw new SQLException("Could not back up legacy database " + source.getName() + ".", exception);
        }
    }

    private void importPlayerData(File legacy) throws SQLException {
        if (!legacy.isFile()) return;
        importTables(levelConnection, legacy, "player_levels", "player_level_reward_claims",
                "player_level_daily_xp", "player_level_prestige", "player_level_pending_rewards");
        importTables(currencyConnection, legacy, "player_balances", "economy_balances");
        importTables(spawnShopConnection, legacy, "npc_links", "spawn_shop_npc_links", "spawn_shop_items");
        attach(legacy, spawnShopConnection);
        try {
            if (tableExists(spawnShopConnection, "legacy", "spawn_npcs")) {
                try (Statement statement = spawnShopConnection.createStatement()) {
                    statement.executeUpdate("INSERT OR IGNORE INTO main.spawn_shop_npc_links (npc_id, shop_id) "
                            + "SELECT npc_id, lower(linked_shop) FROM legacy.spawn_npcs "
                            + "WHERE linked_shop IS NOT NULL AND trim(linked_shop) <> ''");
                    statement.executeUpdate("INSERT OR IGNORE INTO main.npc_links (feature_id, npc_id) "
                            + "SELECT 'spawnshop:' || lower(linked_shop), min(lower(npc_id)) FROM legacy.spawn_npcs "
                            + "WHERE linked_shop IS NOT NULL AND trim(linked_shop) <> '' GROUP BY lower(linked_shop)");
                }
            }
        } finally {
            detach(spawnShopConnection);
        }
    }

    private void importItemPriceData(File legacy) throws SQLException {
        if (legacy.isFile()) {
            importTables(blackMarketConnection, legacy, "item_prices", "black_market_offers",
                    "black_market_bids", "black_market_buy_escrow");
        }
    }

    private void importTables(Connection target, File source, String... tables) throws SQLException {
        attach(source, target);
        try {
            for (String table : tables) copyTable(target, table);
        } finally {
            detach(target);
        }
    }

    private void attach(File source, Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("ATTACH DATABASE ? AS legacy")) {
            statement.setString(1, source.getAbsolutePath());
            statement.execute();
        }
    }

    private void detach(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("DETACH DATABASE legacy");
        }
    }

    private void copyTable(Connection target, String table) throws SQLException {
        if (!tableExists(target, "legacy", table) || !tableExists(target, "main", table)) return;
        List<String> destinationColumns = tableColumns(target, "main", table);
        List<String> sourceColumns = tableColumns(target, "legacy", table);
        List<String> columns = new ArrayList<>();
        for (String column : destinationColumns) if (sourceColumns.contains(column)) columns.add(column);
        if (columns.isEmpty()) return;
        String names = columns.stream().map(name -> "\"" + name + "\"").collect(java.util.stream.Collectors.joining(", "));
        try (Statement statement = target.createStatement()) {
            statement.executeUpdate("INSERT OR IGNORE INTO main.\"" + table + "\" (" + names + ") SELECT "
                    + names + " FROM legacy.\"" + table + "\"");
        }
    }

    private boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + schema
                + ".sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<String> tableColumns(Connection connection, String schema, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("PRAGMA " + schema + ".table_info('" + table + "')")) {
            while (results.next()) columns.add(results.getString("name"));
        }
        return columns;
    }

    private void retireLegacyDatabase(File file) throws SQLException {
        try {
            for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
                File legacyFile = suffix.isEmpty() ? file : new File(file.getPath() + suffix);
                if (legacyFile.exists() && !Files.deleteIfExists(legacyFile.toPath())) {
                    throw new IOException("could not remove " + legacyFile.getName());
                }
            }
        } catch (IOException exception) {
            throw new SQLException("Could not retire migrated database " + file.getName() + ".", exception);
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
        close(levelConnection);
        close(currencyConnection);
        close(spawnShopConnection);
        close(blackMarketConnection);
        levelConnection = null;
        currencyConnection = null;
        spawnShopConnection = null;
        blackMarketConnection = null;
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