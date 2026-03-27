package com.voidplugins.voideconomy.database;

import com.voidplugins.voideconomy.VoidEconomy;
import com.voidplugins.voideconomy.currency.CurrencyManager.TopEntry;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class DatabaseManager {

    private final VoidEconomy plugin;
    private Connection connection;

    // Single-threaded executor ensures all DB ops are serialized (SQLite requirement)
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VoidEconomy-DB");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(VoidEconomy plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getDatabaseFile());
            plugin.getDataFolder().mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                // WAL mode for better concurrency and performance
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=10000");
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA foreign_keys=ON");
                createTables(stmt);
            }

            plugin.getLogger().info("Connected to SQLite database (WAL mode).");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database!", e);
            return false;
        }
    }

    private void createTables(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_balances (
                    uuid         TEXT    NOT NULL,
                    username     TEXT    NOT NULL,
                    currency     TEXT    NOT NULL,
                    balance      REAL    NOT NULL DEFAULT 0.0,
                    last_updated INTEGER NOT NULL DEFAULT (strftime('%s','now')),
                    PRIMARY KEY (uuid, currency)
                )""");

        // Index speeds up the /top query dramatically on large servers
        stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_currency_balance
                ON player_balances (currency, balance DESC)""");
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection", e);
        }
    }

    // ── Load all balances for a player ──────────────────────────────────────

    public CompletableFuture<Map<String, Double>> loadBalances(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> result = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT currency, balance FROM player_balances WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.put(rs.getString("currency"), rs.getDouble("balance"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load balances for " + uuid, e);
            }
            return result;
        }, executor);
    }

    // ── Save all balances for a player (batch upsert) ────────────────────────

    public CompletableFuture<Void> saveBalances(UUID uuid, String username, Map<String, Double> balances) {
        if (balances.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO player_balances (uuid, username, currency, balance, last_updated)
                    VALUES (?, ?, ?, ?, strftime('%s','now'))
                    ON CONFLICT(uuid, currency) DO UPDATE SET
                        username = excluded.username,
                        balance = excluded.balance,
                        last_updated = excluded.last_updated""";
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Map.Entry<String, Double> entry : balances.entrySet()) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, username);
                        ps.setString(3, entry.getKey());
                        ps.setDouble(4, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ignored) {}
                plugin.getLogger().log(Level.WARNING, "Failed to save balances for " + uuid, e);
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        }, executor);
    }

    // ── Single balance operations (for offline players) ──────────────────────

    public CompletableFuture<Double> getBalance(UUID uuid, String currencyId, double defaultBalance) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT balance FROM player_balances WHERE uuid = ? AND currency = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, currencyId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble("balance");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get balance", e);
            }
            return defaultBalance;
        }, executor);
    }

    public CompletableFuture<Void> setBalance(UUID uuid, String username, String currencyId, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO player_balances (uuid, username, currency, balance, last_updated)
                    VALUES (?, ?, ?, ?, strftime('%s','now'))
                    ON CONFLICT(uuid, currency) DO UPDATE SET
                        username = excluded.username,
                        balance = excluded.balance,
                        last_updated = excluded.last_updated""";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, currencyId);
                ps.setDouble(4, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set balance", e);
            }
        }, executor);
    }

    // ── Top list query ───────────────────────────────────────────────────────

    public CompletableFuture<List<TopEntry>> getTop(String currencyId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TopEntry> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT username, balance FROM player_balances WHERE currency = ? " +
                    "ORDER BY balance DESC LIMIT ?")) {
                ps.setString(1, currencyId);
                ps.setInt(2, limit);
                ResultSet rs = ps.executeQuery();
                int rank = 1;
                while (rs.next()) {
                    result.add(new TopEntry(rank++, rs.getString("username"), rs.getDouble("balance")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch top list for " + currencyId, e);
            }
            return result;
        }, executor);
    }

    // ── Look up a UUID by player name (for offline admin commands) ───────────

    public CompletableFuture<UUID> findUUIDByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid FROM player_balances WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to find UUID for name: " + name, e);
            }
            return null;
        }, executor);
    }
}
