package me.bintanq.quantum.services;

import me.bintanq.quantum.database.DatabaseManager;

import java.sql.*;
import java.util.UUID;

public class WarningService {
    private final DatabaseManager db;

    public WarningService(DatabaseManager db) {
        this.db = db;
    }

    public int addWarning(UUID uuid) {
        int current = getWarningPoints(uuid);
        int newPoints = current + 1;

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO warnings (uuid, points, last_warn) VALUES (?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, newPoints);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return newPoints;
    }

    public int getWarningPoints(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT points FROM warnings WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("points");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}