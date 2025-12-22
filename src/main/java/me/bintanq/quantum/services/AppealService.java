package me.bintanq.quantum.services;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.models.Appeal;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AppealService {
    private final QuantumPunish plugin;
    private final DatabaseManager db;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public AppealService(QuantumPunish plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public boolean createAppeal(Player player, String reason) {
        if (isOnCooldown(player.getUniqueId())) {
            return false;
        }

        long timestamp = System.currentTimeMillis();
        String sql = "INSERT INTO appeals (uuid, player_name, reason, timestamp, status) VALUES (?, ?, ?, ?, 'PENDING')";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getName());
            pstmt.setString(3, reason);
            pstmt.setLong(4, timestamp);
            pstmt.executeUpdate();

            // Set Cooldown
            long cooldownMin = plugin.getConfig().getLong("appeal-system.cooldown", 30);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownMin * 60 * 1000));

            // Webhook Notification
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", player.getName());
            placeholders.put("%reason%", reason);
            placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            plugin.getWebhookService().sendCustom("appeal", placeholders);

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean hasPendingAppeal(UUID uuid) {
        String sql = "SELECT id FROM appeals WHERE uuid = ? AND status = 'PENDING'";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public List<Appeal> getPendingAppeals() {
        List<Appeal> appeals = new ArrayList<>();
        String sql = "SELECT * FROM appeals WHERE status = 'PENDING'";

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                appeals.add(new Appeal(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getString("reason"),
                        rs.getLong("timestamp"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return appeals;
    }

    public void resolveAppeal(int id, boolean approved, String staffName) {
        String status = approved ? "APPROVED" : "REJECTED";

        try (Connection conn = db.getConnection()) {
            // 1. Ambil UUID dulu buat notifikasi
            UUID playerUuid = null;
            try (PreparedStatement pstmtGet = conn.prepareStatement("SELECT uuid FROM appeals WHERE id = ?")) {
                pstmtGet.setInt(1, id);
                try (ResultSet rs = pstmtGet.executeQuery()) {
                    if (rs.next()) playerUuid = UUID.fromString(rs.getString("uuid"));
                }
            }

            // 2. Update Status
            try (PreparedStatement pstmtUpdate = conn.prepareStatement("UPDATE appeals SET status = ? WHERE id = ?")) {
                pstmtUpdate.setString(1, status);
                pstmtUpdate.setInt(2, id);
                int affected = pstmtUpdate.executeUpdate();
                plugin.getLogger().info("[QuantumPunish] Resolve Appeal ID " + id + " | Status: " + status + " | Affected: " + affected);
            }

            // 3. Notif Player (Bungkus runTask biar Sync)
            if (playerUuid != null) {
                Player target = Bukkit.getPlayer(playerUuid);
                if (target != null && target.isOnline()) {
                    UUID finalUuid = playerUuid; // Java Lambda requirement
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (approved) {
                            target.sendMessage(plugin.getMessageManager().getMessage("appeal-accepted-player").replace("%staff%", staffName));
                            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        } else {
                            target.sendMessage(plugin.getMessageManager().getMessage("appeal-rejected-player").replace("%staff%", staffName));
                            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                        }
                    });
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isOnCooldown(UUID uuid) {
        if (!cooldowns.containsKey(uuid)) return false;
        if (cooldowns.get(uuid) < System.currentTimeMillis()) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }
}