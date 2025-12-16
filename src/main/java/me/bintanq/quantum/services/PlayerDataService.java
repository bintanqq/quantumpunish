package me.bintanq.quantum.services;

import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.models.PlayerData;

import java.sql.*;
import java.util.*;

public class PlayerDataService {
    private final DatabaseManager db;

    public PlayerDataService(DatabaseManager db) {
        this.db = db;
    }

    public void updatePlayerData(UUID uuid, String name, String ip) {
        try (Connection conn = db.getConnection()) {

            PlayerData existing = getPlayerData(conn, uuid);

            if (existing == null) {
                String initialIpList = ip;

                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO player_data (uuid, last_name, ip_addresses, first_join, last_seen) VALUES (?, ?, ?, ?, ?)")) {

                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, name);
                    stmt.setString(3, initialIpList); // IP List (hanya satu IP)
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setLong(5, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
            } else {
                Set<String> ips = new HashSet<>(Arrays.asList(existing.getIpAddresses().split(",")));
                ips.add(ip);
                String ipList = String.join(",", ips);

                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE player_data SET last_name = ?, ip_addresses = ?, last_seen = ? WHERE uuid = ?")) {
                    stmt.setString(1, name);
                    stmt.setString(2, ipList);
                    stmt.setLong(3, System.currentTimeMillis());
                    stmt.setString(4, uuid.toString());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PlayerData getPlayerData(Connection conn, UUID uuid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PlayerData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("last_name"),
                        rs.getString("ip_addresses"),
                        rs.getLong("first_join"),
                        rs.getLong("last_seen")
                );
            }
        }
        return null;
    }

    public PlayerData getPlayerData(UUID uuid) {
        try (Connection conn = db.getConnection()) {
            return getPlayerData(conn, uuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}