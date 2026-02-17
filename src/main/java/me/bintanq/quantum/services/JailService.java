package me.bintanq.quantum.services;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.database.DatabaseManager;
import me.bintanq.quantum.models.Jail;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * JailService - Production Version with Hologram Integration
 * ============================================================================
 */
public class JailService {
    private final QuantumPunish plugin;
    private final DatabaseManager db;
    private final Map<UUID, Jail> activeJails = new ConcurrentHashMap<>();

    public JailService(QuantumPunish plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadActiveJails();
    }

    /**
     * ============================================================================
     * JAIL PLAYER
     * ============================================================================
     */
    public boolean jailPlayer(String playerName, UUID uuid, String cellName, String staff,
                              String reason, long duration, int laborRequired) {

        Location cellLoc = getCellLocation(cellName);
        if (cellLoc == null) {
            plugin.getLogger().warning("[Jail] Cell " + cellName + " not found!");
            return false;
        }

        long timestamp = System.currentTimeMillis();
        long expires = timestamp + duration;

        Jail jail = new Jail(uuid, playerName, cellName, reason, staff,
                timestamp, expires, laborRequired, 0);

        activeJails.put(uuid, jail);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(
                             "INSERT OR REPLACE INTO jails (uuid, player_name, cell_name, reason, staff, " +
                                     "timestamp, expires, labor_required, labor_progress) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, playerName);
                    pstmt.setString(3, cellName);
                    pstmt.setString(4, reason);
                    pstmt.setString(5, staff);
                    pstmt.setLong(6, timestamp);
                    pstmt.setLong(7, expires);
                    pstmt.setInt(8, laborRequired);
                    pstmt.setInt(9, 0);
                    pstmt.executeUpdate();
                }

                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(
                             "INSERT INTO punishments (uuid, player_name, type, reason, staff, timestamp, expires, active) " +
                                     "VALUES (?, ?, 'JAIL', ?, ?, ?, ?, 1)")) {

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, playerName);
                    pstmt.setString(3, reason + " (Cell: " + cellName + ", Labor: " + laborRequired + ")");
                    pstmt.setString(4, staff);
                    pstmt.setLong(5, timestamp);
                    pstmt.setLong(6, expires);
                    pstmt.executeUpdate();
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getConfig().getBoolean("jail-system.broadcast", true)) {
                        String broadcast = plugin.getMessageManager().getMessage("jail-broadcast")
                                .replace("%player%", playerName)
                                .replace("%staff%", staff)
                                .replace("%cell%", cellName)
                                .replace("%reason%", reason);
                        Bukkit.broadcastMessage(broadcast);
                    }

                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        teleportWithRetry(player, cellLoc, playerName, cellName, uuid, 0);
                        sendJailMessage(player, jail);
                    }
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Database error while jailing " + playerName);
                e.printStackTrace();
                activeJails.remove(uuid);
            }
        });

        return true;
    }

    /**
     * ============================================================================
     * RELEASE PLAYER
     * ============================================================================
     */
    public boolean releasePlayer(UUID uuid, String releaser) {
        Jail jail = activeJails.get(uuid);
        if (jail == null) return false;

        final String playerName = jail.getPlayerName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(
                             "DELETE FROM jails WHERE uuid = ?")) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.executeUpdate();
                }

                activeJails.remove(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // ═══════════════════════════════════════════════════════════
                    // HOLOGRAM: Remove all holograms for player on unjail
                    // ═══════════════════════════════════════════════════════════
                    plugin.getLaborManager().removeAllHologramsForPlayer(uuid);

                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        Location releaseLocation = getReleaseLocation();

                        if (releaseLocation == null) {
                            releaseLocation = player.getWorld().getSpawnLocation();
                            plugin.getLogger().warning("[Jail] No release location set, using world spawn");
                        }

                        final Location finalLocation = releaseLocation;
                        if (!finalLocation.getChunk().isLoaded()) {
                            finalLocation.getChunk().load(true);
                        }

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            boolean success = player.teleport(finalLocation);
                            if (success) {
                                player.sendMessage(plugin.getMessageManager().getMessage("jail-released")
                                        .replace("%releaser%", releaser));
                            } else {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    player.teleport(finalLocation);
                                    player.sendMessage(plugin.getMessageManager().getMessage("jail-released")
                                            .replace("%releaser%", releaser));
                                }, 20L);
                            }
                        }, 5L);
                    }

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%player%", playerName);
                    placeholders.put("%releaser%", releaser);
                    placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date()));
                    plugin.getWebhookService().sendCustom("unjail", placeholders);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Database error while releasing " + playerName);
                e.printStackTrace();
            }
        });

        return true;
    }

    /**
     * ============================================================================
     * INCREMENT LABOR
     * ============================================================================
     */
    public void incrementLabor(UUID uuid) {
        Jail jail = activeJails.get(uuid);
        if (jail == null) {
            plugin.getLogger().warning("[Jail] No jail found for UUID: " + uuid);
            return;
        }

        final int currentProgress = jail.getLaborProgress();
        final int required = jail.getLaborRequired();

        if (currentProgress >= required) {
            return;
        }

        jail.incrementLabor();
        final int newProgress = jail.getLaborProgress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(
                             "UPDATE jails SET labor_progress = ? WHERE uuid = ?")) {

                    pstmt.setInt(1, Math.min(newProgress, required));
                    pstmt.setString(2, uuid.toString());
                    int rows = pstmt.executeUpdate();

                    if (rows == 0) {
                        plugin.getLogger().warning("[Jail] Failed to update progress for " + uuid);
                    }
                }

                Jail updatedJail = activeJails.get(uuid);
                if (updatedJail != null && updatedJail.canBeReleased()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        releasePlayer(uuid, "SYSTEM");
                    });
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Database error during increment");
                e.printStackTrace();
            }
        });
    }

    /**
     * ============================================================================
     * GETTERS
     * ============================================================================
     */
    public boolean isJailed(UUID uuid) {
        return activeJails.containsKey(uuid);
    }

    public Jail getJail(UUID uuid) {
        return activeJails.get(uuid);
    }

    public Set<String> getCellNames() {
        if (!plugin.getConfig().contains("jail-system.cells")) {
            return Collections.emptySet();
        }
        return plugin.getConfig().getConfigurationSection("jail-system.cells").getKeys(false);
    }

    /**
     * ============================================================================
     * LOCATION MANAGEMENT
     * ============================================================================
     */
    public Location getCellLocation(String cellName) {
        String path = "jail-system.cells." + cellName;

        if (!plugin.getConfig().contains(path)) {
            return null;
        }

        String worldName = plugin.getConfig().getString(path + ".world");
        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw");
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch");

        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    public void setCellLocation(String cellName, Location location) {
        String path = "jail-system.cells." + cellName;

        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());

        plugin.saveConfig();
    }

    public Location getReleaseLocation() {
        if (!plugin.getConfig().contains("jail-system.release-location")) {
            return null;
        }

        String worldName = plugin.getConfig().getString("jail-system.release-location.world");
        double x = plugin.getConfig().getDouble("jail-system.release-location.x");
        double y = plugin.getConfig().getDouble("jail-system.release-location.y");
        double z = plugin.getConfig().getDouble("jail-system.release-location.z");
        float yaw = (float) plugin.getConfig().getDouble("jail-system.release-location.yaw");
        float pitch = (float) plugin.getConfig().getDouble("jail-system.release-location.pitch");

        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    public void setReleaseLocation(Location location) {
        plugin.getConfig().set("jail-system.release-location.world", location.getWorld().getName());
        plugin.getConfig().set("jail-system.release-location.x", location.getX());
        plugin.getConfig().set("jail-system.release-location.y", location.getY());
        plugin.getConfig().set("jail-system.release-location.z", location.getZ());
        plugin.getConfig().set("jail-system.release-location.yaw", location.getYaw());
        plugin.getConfig().set("jail-system.release-location.pitch", location.getPitch());
        plugin.saveConfig();
    }

    /**
     * ============================================================================
     * LOAD ACTIVE JAILS
     * ============================================================================
     */
    private void loadActiveJails() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM jails");
                 ResultSet rs = pstmt.executeQuery()) {

                int loaded = 0;
                List<UUID> toRelease = new ArrayList<>();

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Jail jail = new Jail(
                            uuid,
                            rs.getString("player_name"),
                            rs.getString("cell_name"),
                            rs.getString("reason"),
                            rs.getString("staff"),
                            rs.getLong("timestamp"),
                            rs.getLong("expires"),
                            rs.getInt("labor_required"),
                            rs.getInt("labor_progress")
                    );

                    if (jail.canBeReleased()) {
                        toRelease.add(uuid);
                    } else {
                        activeJails.put(uuid, jail);
                        loaded++;
                    }
                }

                if (!toRelease.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (UUID uuid : toRelease) {
                            releasePlayer(uuid, "SYSTEM");
                        }
                    });
                }

                startExpirationTask();

            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Failed to load active jails!");
                e.printStackTrace();
            }
        });
    }

    /**
     * ============================================================================
     * EXPIRATION TASK
     * ============================================================================
     */
    private void startExpirationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<UUID> toRelease = new ArrayList<>();

            for (Map.Entry<UUID, Jail> entry : activeJails.entrySet()) {
                Jail jail = entry.getValue();
                if (jail.canBeReleased()) {
                    toRelease.add(entry.getKey());
                }
            }

            if (!toRelease.isEmpty()) {
                for (UUID uuid : toRelease) {
                    releasePlayer(uuid, "SYSTEM");
                }
            }
        }, 100L, 100L);
    }

    /**
     * ============================================================================
     * TELEPORT WITH RETRY
     * ============================================================================
     */
    private void teleportWithRetry(Player player, Location location, String playerName,
                                   String cellName, UUID playerUuid, int attempt) {
        if (attempt >= 5) {
            plugin.getLogger().severe("[Jail] Failed to teleport " + playerName + " after 5 attempts!");
            player.sendMessage(plugin.getMessageManager().colorize(
                    "&cFailed to teleport to jail. Please contact an admin."));
            return;
        }

        if (!location.getChunk().isLoaded()) {
            location.getChunk().load(true);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean success = player.teleport(location);
            if (success) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getLaborManager().createAllHologramsForPlayer(playerUuid, cellName);
                }, 10L); // Small delay to ensure player is fully loaded
            } else {
                plugin.getLogger().warning("[Jail] Teleport failed (attempt " + (attempt + 1) + "), retrying...");
                long delay = (attempt + 1) * 20L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        teleportWithRetry(player, location, playerName, cellName, playerUuid, attempt + 1);
                    }
                }, delay);
            }
        }, 5L);
    }

    /**
     * ============================================================================
     * SEND JAIL MESSAGE
     * ============================================================================
     */
    private void sendJailMessage(Player player, Jail jail) {
        List<String> messages = plugin.getMessageManager().getMessagesList("jailed");

        for (String msg : messages) {
            player.sendMessage(msg
                    .replace("%cell%", jail.getCellName())
                    .replace("%reason%", jail.getReason())
                    .replace("%staff%", jail.getStaff())
                    .replace("%duration%", formatDuration(jail.getExpires() - jail.getTimestamp()))
                    .replace("%labor%", String.valueOf(jail.getLaborRequired())));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", jail.getPlayerName());
        placeholders.put("%staff%", jail.getStaff());
        placeholders.put("%cell%", jail.getCellName());
        placeholders.put("%reason%", jail.getReason());
        placeholders.put("%duration%", formatDuration(jail.getExpires() - jail.getTimestamp()));
        placeholders.put("%labor%", String.valueOf(jail.getLaborRequired()));
        placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date()));
        plugin.getWebhookService().sendCustom("jail", placeholders);
    }

    private String formatDuration(long millis) {
        if (millis < 0) return "Expired";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}