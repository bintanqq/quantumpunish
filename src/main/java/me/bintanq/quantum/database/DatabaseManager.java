package me.bintanq.quantum.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Punishment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;

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
            """,
                """
            CREATE TABLE IF NOT EXISTS appeals (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid VARCHAR(36) NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            reason TEXT NOT NULL,
            timestamp BIGINT NOT NULL,
            status VARCHAR(20) DEFAULT 'PENDING'
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

    public List<String> getAltsByIP(String ipList) {
        List<String> alts = new ArrayList<>();
        String[] ips = ipList.split(", ");
        String targetIp = ips[ips.length - 1]; // Mengambil IP terbaru

        String sql = "SELECT last_name FROM player_data WHERE ip_addresses LIKE ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + targetIp + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    alts.add(rs.getString("last_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return alts;
    }

    public void cleanupOldData(int days) {
        if (days <= 0) return;
        long threshold = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);

        String sql = "DELETE FROM punishments WHERE expires < ? AND expires != 0 AND active = 0";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, threshold);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Database Cleanup: Removed " + deleted + " old inactive records.");

                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("%deleted%", String.valueOf(deleted));
                placeholders.put("%days%", String.valueOf(days));
                placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));

                plugin.getWebhookService().sendCustom("cleanup", placeholders);
            } else {
                plugin.getLogger().info("Database Cleanup: No records were deleted.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database Cleanup Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasActivePunishment(String playerName, String type) {
        String sql = "SELECT id FROM punishments WHERE player_name = ? AND type = ? AND active = 1 LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            pstmt.setString(2, type);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<Punishment> getAllActivePunishments() {
        List<Punishment> activeList = new ArrayList<>();
        String sql = "SELECT * FROM punishments WHERE active = 1 AND (expires > ? OR expires IS NULL)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, System.currentTimeMillis());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    activeList.add(new Punishment(
                            java.util.UUID.fromString(rs.getString("uuid")),
                            rs.getString("player_name"),
                            me.bintanq.quantum.models.PunishmentType.valueOf(rs.getString("type")),
                            rs.getString("reason"),
                            rs.getString("staff"),
                            rs.getLong("timestamp"),
                            rs.getObject("expires") != null ? rs.getLong("expires") : null,
                            rs.getString("ip_address")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return activeList;
    }
}