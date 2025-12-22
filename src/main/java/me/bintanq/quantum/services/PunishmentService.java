package me.bintanq.quantum.services;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.models.Punishment;
import me.bintanq.quantum.models.PunishmentType;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class PunishmentService {
    private final QuantumPunish plugin;
    private final DatabaseManager db;
    private final WebhookService webhookService;
    private final WarningService warningService;

    public PunishmentService(QuantumPunish plugin, DatabaseManager db, WebhookService webhookService, WarningService warningService) {
        this.plugin = plugin;
        this.db = db;
        this.webhookService = webhookService;
        this.warningService = warningService;
    }

    public void banPlayer(String playerName, UUID uuid, String staff, String reason, Long expires, String ip) {
        Punishment punishment = new Punishment(uuid, playerName, PunishmentType.BAN, reason, staff, System.currentTimeMillis(), expires, ip);
        savePunishment(punishment);

        if (expires == null) {
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, null, staff);
        } else {
            Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, reason, new Date(expires), staff);
        }

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            String kickMsg = plugin.getMessageManager().getMessage("ban-kick")
                    .replace("%reason%", reason)
                    .replace("%staff%", staff);
            target.kickPlayer(kickMsg);
        }

        broadcastPunishment(punishment);
        webhookService.sendPunishment(punishment);
    }

    public void mutePlayer(String playerName, UUID uuid, String staff, String reason, Long expires) {
        Punishment punishment = new Punishment(uuid, playerName, PunishmentType.MUTE, reason, staff, System.currentTimeMillis(), expires, null);
        savePunishment(punishment);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(plugin.getMessageManager().getMessage("muted")
                    .replace("%reason%", reason)
                    .replace("%staff%", staff)
                    .replace("%duration%", expires == null ? "Permanent" : formatDuration(expires - System.currentTimeMillis())));
        }

        if (expires == null || expires - System.currentTimeMillis() > 60000) {
            broadcastPunishment(punishment);
        }
        webhookService.sendPunishment(punishment);
    }

    public void kickPlayer(String playerName, UUID uuid, String staff, String reason) {
        Punishment punishment = new Punishment(uuid, playerName, PunishmentType.KICK, reason, staff, System.currentTimeMillis(), null, null);
        savePunishment(punishment);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            String kickMsg = plugin.getMessageManager().getMessage("kick-message")
                    .replace("%reason%", reason)
                    .replace("%staff%", staff);
            target.kickPlayer(kickMsg);
        }

        broadcastPunishment(punishment);
        webhookService.sendPunishment(punishment);
    }

    public void warnPlayer(String playerName, UUID uuid, String staff, String reason, int pointsAdded) {
        Punishment punishment = new Punishment(uuid, playerName, PunishmentType.WARN, reason, staff, System.currentTimeMillis(), null, null);
        savePunishment(punishment);

        int totalPoints = warningService.getWarningPoints(uuid);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(plugin.getMessageManager().getMessage("warned")
                    .replace("%reason%", reason)
                    .replace("%staff%", staff)
                    .replace("%points%", String.valueOf(pointsAdded))
                    .replace("%points_added%", String.valueOf(pointsAdded))
                    .replace("%total%", String.valueOf(totalPoints))
                    .replace("%total_points%", String.valueOf(totalPoints)));
        }

        checkAutoPunishment(uuid, playerName, totalPoints, staff);
        webhookService.sendPunishment(punishment);
    }

    private void checkAutoPunishment(UUID uuid, String playerName, int points, String staff) {
        var autoPunishments = plugin.getConfig().getConfigurationSection("auto-punishments");
        if (autoPunishments == null) return;

        for (String key : autoPunishments.getKeys(false)) {
            int threshold = autoPunishments.getInt(key + ".points");
            if (points == threshold) {
                String action = autoPunishments.getString(key + ".action");
                String reason = autoPunishments.getString(key + ".reason", "Auto-punishment");
                long duration = parseDuration(autoPunishments.getString(key + ".duration", "0"));

                switch (action.toUpperCase()) {
                    case "MUTE" -> {
                        Long expiry = (duration > 0) ? System.currentTimeMillis() + duration : null;
                        mutePlayer(playerName, uuid, "SYSTEM", reason, expiry);
                    }                    case "KICK" -> kickPlayer(playerName, uuid, "SYSTEM", reason);
                    case "BAN" -> {
                        Long expiry = (duration > 0) ? System.currentTimeMillis() + duration : null;
                        banPlayer(playerName, uuid, "SYSTEM", reason, expiry, null);
                    }                }

                Punishment autoPunish = new Punishment(uuid, playerName, PunishmentType.AUTO, reason, "SYSTEM", System.currentTimeMillis(), null, null);
                webhookService.sendPunishment(autoPunish);
                break;
            }
        }
    }

    public void unbanPlayer(String playerName, String staff) {
        removeActivePunishment(playerName, PunishmentType.BAN, staff);
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
        webhookService.sendUnpunishment("unban", playerName, staff);
    }

    public void unmutePlayer(String playerName, UUID uuid, String staff) {
        removeActivePunishment(playerName, PunishmentType.MUTE, staff);

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(plugin.getMessageManager().getMessage("unmuted").replace("%staff%", staff));
        }
        webhookService.sendUnpunishment("unmute", playerName, staff);
    }

    public void banIP(String ip, String staff, String reason) {
        Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, null, staff);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getAddress().getAddress().getHostAddress().equals(ip)) {
                String kickMsg = plugin.getMessageManager().getMessage("banip-kick")
                        .replace("%reason%", reason)
                        .replace("%staff%", staff);
                player.kickPlayer(kickMsg);
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%ip%", ip);
        placeholders.put("%staff%", staff);
        placeholders.put("%reason%", reason);
        webhookService.sendCustom("banip", placeholders);
    }

    public void unbanIP(String ip, String staff) {
        Bukkit.getBanList(BanList.Type.IP).pardon(ip);
        webhookService.sendUnpunishment("unbanip", ip, staff);
    }

    public boolean isMuted(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, expires FROM punishments WHERE uuid = ? AND type = 'MUTE' AND active = 1")) {
            stmt.setString(1, uuid.toString());

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Long expires = rs.getLong("expires");
                if (expires == 0 || expires > System.currentTimeMillis()) {
                    return true;
                } else {
                    final int punishmentId = rs.getInt("id");

                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            expirePunishment(punishmentId);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to async expire punishment: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Database access error in isMuted: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public List<Punishment> getHistory(UUID uuid) {
        List<Punishment> history = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM punishments WHERE uuid = ? ORDER BY timestamp DESC")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                history.add(punishmentFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    private void savePunishment(Punishment punishment) {
        try {
            Long expires = punishment.getExpires();

            executeUpdateWithRetry(
                    "INSERT INTO punishments (uuid, player_name, type, reason, staff, timestamp, expires, ip_address, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    punishment.getUuid().toString(),
                    punishment.getPlayerName(),
                    punishment.getType().name(),
                    punishment.getReason(),
                    punishment.getStaff(),
                    punishment.getTimestamp(),
                    expires == null ? 0L : expires,
                    punishment.getIpAddress()
            );
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save punishment for " + punishment.getPlayerName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeActivePunishment(String playerName, PunishmentType type, String staff) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE punishments SET active = 0, removed_by = ?, removed_at = ? WHERE player_name = ? AND type = ? AND active = 1")) {
            stmt.setString(1, staff);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, playerName);
            stmt.setString(4, type.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void expirePunishment(int id) throws SQLException {
        UUID uuid = null;
        String playerName = null;

        try (Connection conn = db.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement("SELECT uuid, player_name FROM punishments WHERE id = ?")) {
            selectStmt.setInt(1, id);
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
                playerName = rs.getString("player_name");
            }
        }

        executeUpdateWithRetry(
                "UPDATE punishments SET active = 0, removed_by = 'SYSTEM', removed_at = ? WHERE id = ?",
                System.currentTimeMillis(), id
        );

        if (uuid != null) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                target.sendMessage(plugin.getMessageManager().getMessage("unmute-success"));
            }
        }
    }

    private void broadcastPunishment(Punishment punishment) {
        String msg = plugin.getMessageManager().getPunishmentBroadcast(punishment);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private Punishment punishmentFromResultSet(ResultSet rs) throws SQLException {
        return new Punishment(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("staff"),
                rs.getLong("timestamp"),
                rs.getLong("expires") == 0 ? null : rs.getLong("expires"),
                rs.getString("ip_address")
        );
    }

    public long parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;

        long multiplier = 60000;
        String value = input;

        if (input.endsWith("s")) {
            multiplier = 1000;
            value = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 60000;
            value = input.substring(0, input.length() - 1);
        } else if (input.endsWith("h")) {
            multiplier = 3600000;
            value = input.substring(0, input.length() - 1);
        } else if (input.endsWith("d")) {
            multiplier = 86400000;
            value = input.substring(0, input.length() - 1);
        } else if (input.endsWith("w")) {
            multiplier = 604800000;
            value = input.substring(0, input.length() - 1);
        }

        try {
            return Long.parseLong(value) * multiplier;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String formatDuration(long millis) {
        if (millis <= 0) return "Permanent";

        millis += 999;

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + " day(s)";
        if (hours > 0) return hours + " hour(s)";
        if (minutes > 0) return minutes + " minute(s)";
        return seconds + " second(s)";
    }

    public String getInitialDuration(Punishment p) {
        if (p.getExpires() == null || p.getExpires() == 0) return "Permanent";

        long diff = p.getExpires() - p.getTimestamp();
        if (diff <= 0) return "0s";

        diff += 999;

        long seconds = diff / 1000;
        long days = seconds / (24 * 3600);
        seconds %= (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");

        // Kalau hukuman sangat singkat (detik)
        if (sb.length() == 0 && seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    private void executeUpdateWithRetry(String sql, Object... params) throws SQLException {
        int maxRetries = 5;
        for (int i = 0; i < maxRetries; i++) {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                for (int j = 0; j < params.length; j++) {
                    stmt.setObject(j + 1, params[j]);
                }
                stmt.executeUpdate();
                return; // Sukses
            } catch (SQLException e) {
                if (e.getErrorCode() == 5 || e.getErrorCode() == 6) {
                    if (i < maxRetries - 1) {
                        try {
                            Thread.sleep(100); // Tunggu 100ms
                            continue; // Coba lagi
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                throw e;
            }
        }
    }
}