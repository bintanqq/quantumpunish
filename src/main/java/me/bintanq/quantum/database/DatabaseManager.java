package me.bintanq.quantum.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantum.QuantumPunish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {
    private final QuantumPunish plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            setupDatabase();
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void setupDatabase() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/punishments.db"); // Nama file database yang lebih spesifik
        config.setDriverClassName("org.sqlite.JDBC");

        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        config.setConnectionTimeout(30000);

        config.addDataSourceProperty("foreign_keys", "true");

        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        String[] tables = {
                """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                last_name VARCHAR(16) NOT NULL,
                ip_addresses TEXT NOT NULL,
                first_join BIGINT NOT NULL,
                last_seen BIGINT NOT NULL
            )
            """,
                """
            CREATE TABLE IF NOT EXISTS punishments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                type VARCHAR(20) NOT NULL,
                reason TEXT NOT NULL,
                staff VARCHAR(16) NOT NULL,
                timestamp BIGINT NOT NULL,
                expires BIGINT,
                ip_address VARCHAR(45),
                active BOOLEAN NOT NULL DEFAULT 1,
                removed_by VARCHAR(16),
                removed_at BIGINT
            )
            """,
                """
            CREATE TABLE IF NOT EXISTS warnings (
                uuid VARCHAR(36) PRIMARY KEY,
                points INTEGER NOT NULL DEFAULT 0,
                last_warn BIGINT
            )
            """
        };

        try (Connection conn = getConnection()) {
            for (String sql : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.execute();
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}