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

public class JailService {
    private final QuantumPunish plugin;
    private final DatabaseManager db;
    private final Map<UUID, Jail> activeJails = new ConcurrentHashMap<>();

    public JailService(QuantumPunish plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadActiveJails();
    }

    public boolean jailPlayer(String playerName, UUID uuid, String cellName, String staff,
                              String reason, long duration, int laborRequired) {

        Location cellLoc = getCellLocation(cellName);
        if (cellLoc == null) {
            plugin.getLogger().warning("Cell " + cellName + " not found!");
            return false;
        }

        long timestamp = System.currentTimeMillis();
        long expires = timestamp + duration;
        long remainingSeconds = duration / 1000;

        Jail jail = new Jail(uuid, playerName, cellName, reason, staff,
                timestamp, expires, laborRequired, 0);

        activeJails.put(uuid, jail);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT OR REPLACE INTO jails (uuid, player_name, cell_name, reason, staff, " +
                                "timestamp, expires, labor_required, labor_progress, remaining_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, playerName);
                    pstmt.setString(3, cellName);
                    pstmt.setString(4, reason);
                    pstmt.setString(5, staff);
                    pstmt.setLong(6, timestamp);
                    pstmt.setLong(7, expires);
                    pstmt.setInt(8, laborRequired);
                    pstmt.setInt(9, 0);
                    pstmt.setLong(10, remainingSeconds);
                    pstmt.executeUpdate();
                }

                try (PreparedStatement pstmt = conn.prepareStatement(
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

                // Back to Sync for Bukkit API
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
                plugin.getLogger().severe("[Jail] SQL Error: " + e.getMessage());
                activeJails.remove(uuid);
            }
        });

        return true;
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Jail jail = activeJails.get(uuid);
        if (jail == null) return;

        boolean countOffline = plugin.getConfig().getBoolean("jail-system.count-offline-time", true);

        if (!countOffline) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("SELECT remaining_seconds FROM jails WHERE uuid = ?")) {
                    pstmt.setString(1, uuid.toString());
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        long remainingSecs = rs.getLong("remaining_seconds");
                        long newExpires = System.currentTimeMillis() + (remainingSecs * 1000);
                        jail.setExpires(newExpires); // Update Model

                        // Sync DB
                        try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE jails SET expires = ? WHERE uuid = ?")) {
                            updateStmt.setLong(1, newExpires);
                            updateStmt.setString(2, uuid.toString());
                            updateStmt.executeUpdate();
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("[Jail] Join error: " + e.getMessage());
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLaborManager().createAllHologramsForPlayer(uuid, jail.getCellName());
                });
            });
        } else {
            plugin.getLaborManager().createAllHologramsForPlayer(uuid, jail.getCellName());
        }
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Jail jail = activeJails.get(uuid);
        if (jail == null) return;

        boolean countOffline = plugin.getConfig().getBoolean("jail-system.count-offline-time", true);
        if (!countOffline) {
            long remainingSecs = Math.max(0, (jail.getExpires() - System.currentTimeMillis()) / 1000);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = db.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE jails SET remaining_seconds = ? WHERE uuid = ?")) {
                    pstmt.setLong(1, remainingSecs);
                    pstmt.setString(2, uuid.toString());
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().severe("[Jail] Quit error: " + e.getMessage());
                }
            });
        }
        plugin.getLaborManager().removeAllHologramsForPlayer(uuid);
    }

    public boolean releasePlayer(UUID uuid, String releaser) {
        Jail jail = activeJails.get(uuid);
        if (jail == null) return false;

        final String playerName = jail.getPlayerName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("DELETE FROM jails WHERE uuid = ?")) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();

                activeJails.remove(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLaborManager().removeAllHologramsForPlayer(uuid);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        Location releaseLocation = getReleaseLocation();
                        if (releaseLocation == null) releaseLocation = player.getWorld().getSpawnLocation();

                        if (!releaseLocation.getChunk().isLoaded()) releaseLocation.getChunk().load(true);

                        final Location finalLoc = releaseLocation;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.teleport(finalLoc)) {
                                player.sendMessage(plugin.getMessageManager().getMessage("jail-released").replace("%releaser%", releaser));
                            } else {
                                Bukkit.getScheduler().runTaskLater(plugin, () -> player.teleport(finalLoc), 20L);
                            }
                        }, 5L);
                    }

                    // Webhook notification
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%player%", playerName);
                    placeholders.put("%releaser%", releaser);
                    placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                    plugin.getWebhookService().sendCustom("unjail", placeholders);
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Release SQL error: " + e.getMessage());
            }
        });
        return true;
    }

    public void incrementLabor(UUID uuid) {
        Jail jail = activeJails.get(uuid);
        if (jail == null) return;

        if (jail.getLaborProgress() >= jail.getLaborRequired()) return;

        jail.incrementLabor();
        final int newProgress = jail.getLaborProgress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("UPDATE jails SET labor_progress = ? WHERE uuid = ?")) {
                pstmt.setInt(1, newProgress);
                pstmt.setString(2, uuid.toString());
                pstmt.executeUpdate();

                if (jail.canBeReleased()) {
                    Bukkit.getScheduler().runTask(plugin, () -> releasePlayer(uuid, "SYSTEM"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Jail] Labor increment error: " + e.getMessage());
            }
        });
    }

    public Location getCellLocation(String cellName) {
        String path = "jail-system.cells." + cellName;
        if (!plugin.getConfig().contains(path)) return null;
        return new Location(Bukkit.getWorld(plugin.getConfig().getString(path + ".world")),
                plugin.getConfig().getDouble(path + ".x"), plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"), (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch"));
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
        if (!plugin.getConfig().contains("jail-system.release-location")) return null;
        String path = "jail-system.release-location";
        return new Location(Bukkit.getWorld(plugin.getConfig().getString(path + ".world")),
                plugin.getConfig().getDouble(path + ".x"), plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"), (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch"));
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

    public Set<String> getCellNames() {
        if (!plugin.getConfig().contains("jail-system.cells")) return Collections.emptySet();
        return plugin.getConfig().getConfigurationSection("jail-system.cells").getKeys(false);
    }

    private void loadActiveJails() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM jails");
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Jail jail = new Jail(uuid, rs.getString("player_name"), rs.getString("cell_name"),
                            rs.getString("reason"), rs.getString("staff"), rs.getLong("timestamp"),
                            rs.getLong("expires"), rs.getInt("labor_required"), rs.getInt("labor_progress"));

                    if (plugin.getConfig().getBoolean("jail-system.count-offline-time", true) && jail.canBeReleased()) {
                        Bukkit.getScheduler().runTask(plugin, () -> releasePlayer(uuid, "SYSTEM"));
                    } else {
                        activeJails.put(uuid, jail);
                    }
                }
                startExpirationTask();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void startExpirationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Jail jail : activeJails.values()) {
                if (jail.canBeReleased()) releasePlayer(jail.getUuid(), "SYSTEM");
            }
        }, 100L, 100L);
    }

    private void teleportWithRetry(Player player, Location location, String playerName,
                                   String cellName, UUID playerUuid, int attempt) {
        if (attempt >= 5) return;
        if (!location.getChunk().isLoaded()) location.getChunk().load(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.teleport(location)) {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        plugin.getLaborManager().createAllHologramsForPlayer(playerUuid, cellName), 10L);
            } else {
                long delay = (attempt + 1) * 20L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) teleportWithRetry(player, location, playerName, cellName, playerUuid, attempt + 1);
                }, delay);
            }
        }, 5L);
    }

    private void sendJailMessage(Player player, Jail jail) {
        List<String> messages = plugin.getMessageManager().getMessagesList("jailed");
        for (String msg : messages) {
            player.sendMessage(msg.replace("%cell%", jail.getCellName()).replace("%reason%", jail.getReason())
                    .replace("%staff%", jail.getStaff()).replace("%duration%", formatDuration(jail.getExpires() - jail.getTimestamp()))
                    .replace("%labor%", String.valueOf(jail.getLaborRequired())));
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", jail.getPlayerName());
        placeholders.put("%staff%", jail.getStaff());
        placeholders.put("%cell%", jail.getCellName());
        placeholders.put("%reason%", jail.getReason());
        placeholders.put("%duration%", formatDuration(jail.getExpires() - jail.getTimestamp()));
        placeholders.put("%labor%", String.valueOf(jail.getLaborRequired()));
        placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        plugin.getWebhookService().sendCustom("jail", placeholders);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "d";
        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    public boolean isJailed(UUID uuid) { return activeJails.containsKey(uuid); }
    public Jail getJail(UUID uuid) { return activeJails.get(uuid); }
}