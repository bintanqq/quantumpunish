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

public class JailService {
    private final QuantumPunish plugin;
    private final DatabaseManager db;
    private final Map<UUID, Jail> activeJails = new HashMap<>();

    public JailService(QuantumPunish plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        loadActiveJails();
    }

    /**
     * Jail a player
     */
    public boolean jailPlayer(String playerName, UUID uuid, String cellName, String staff,
                              String reason, long duration, int laborRequired) {

        Location cellLoc = getCellLocation(cellName);
        if (cellLoc == null) {
            plugin.getLogger().warning("Cell " + cellName + " not found!");
            return false;
        }

        long timestamp = System.currentTimeMillis();
        long expires = timestamp + duration;

        Jail jail = new Jail(uuid, playerName, cellName, reason, staff,
                timestamp, expires, laborRequired, 0);

        // Save to jails database
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

            plugin.getLogger().info("[Jail] Saved to jails table: " + playerName);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        // ALSO save to punishments history table
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

            plugin.getLogger().info("[Jail] Saved to punishments history: " + playerName);
        } catch (SQLException e) {
            plugin.getLogger().warning("[Jail] Failed to save to punishments history: " + e.getMessage());
        }

        activeJails.put(uuid, jail);

        // Broadcast first (if enabled)
        if (plugin.getConfig().getBoolean("jail-system.broadcast", true)) {
            String broadcast = plugin.getMessageManager().getMessage("jail-broadcast")
                    .replace("%player%", playerName)
                    .replace("%staff%", staff)
                    .replace("%cell%", cellName)
                    .replace("%reason%", reason);
            Bukkit.broadcastMessage(broadcast);
        }

        // Teleport player to jail with retry mechanism
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                teleportWithRetry(player, cellLoc, playerName, cellName, 0);
                sendJailMessage(player, jail);
            });
        } else {
            plugin.getLogger().info("[Jail] " + playerName + " will be teleported on next login");
        }

        return true;
    }

    /**
     * Teleport with retry mechanism
     */
    private void teleportWithRetry(Player player, Location location, String playerName, String cellName, int attempt) {
        if (attempt >= 5) {
            plugin.getLogger().severe("[Jail] Failed to teleport " + playerName + " after 5 attempts!");
            player.sendMessage(plugin.getMessageManager().colorize("&cFailed to teleport to jail. Please contact an admin."));
            return;
        }

        // CRITICAL: Load chunk first!
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load(true);
            plugin.getLogger().info("[Jail] Loading chunk for " + cellName + " before teleport");
        }

        // Add small delay for chunk to fully load
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean success = player.teleport(location);
            if (success) {
                plugin.getLogger().info("[Jail] " + playerName + " teleported to cell " + cellName + " (attempt " + (attempt + 1) + ")");
            } else {
                plugin.getLogger().warning("[Jail] Failed to teleport " + playerName + " to cell " + cellName + " (attempt " + (attempt + 1) + "), retrying...");
                // Retry with increasing delay
                long delay = (attempt + 1) * 20L; // 1s, 2s, 3s, 4s, 5s
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        teleportWithRetry(player, location, playerName, cellName, attempt + 1);
                    }
                }, delay);
            }
        }, 5L); // 250ms delay for chunk load
    }

    /**
     * Release a player from jail
     */
    public boolean releasePlayer(UUID uuid, String releaser) {
        Jail jail = activeJails.get(uuid);
        if (jail == null) return false;

        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM jails WHERE uuid = ?")) {

            pstmt.setString(1, uuid.toString());
            pstmt.executeUpdate();

            activeJails.remove(uuid);

            plugin.getLogger().info("[Jail] Released " + jail.getPlayerName() + " by " + releaser);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Get release location dari config
                Location releaseLocation = getReleaseLocation();

                if (releaseLocation == null) {
                    // Fallback ke world spawn
                    releaseLocation = player.getWorld().getSpawnLocation();
                    plugin.getLogger().warning("[Jail] No release location set, using world spawn");
                }

                // Load chunk first
                final Location finalLocation = releaseLocation;
                if (!finalLocation.getChunk().isLoaded()) {
                    finalLocation.getChunk().load(true);
                    plugin.getLogger().info("[Jail] Loading chunk for release location");
                }

                // Teleport with delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boolean success = player.teleport(finalLocation);
                    if (success) {
                        plugin.getLogger().info("[Jail] " + jail.getPlayerName() + " teleported to release location");
                        player.sendMessage(plugin.getMessageManager().getMessage("jail-released")
                                .replace("%releaser%", releaser));
                    } else {
                        plugin.getLogger().warning("[Jail] Failed to teleport " + jail.getPlayerName() + " to release location, retrying...");
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            player.teleport(finalLocation);
                            player.sendMessage(plugin.getMessageManager().getMessage("jail-released")
                                    .replace("%releaser%", releaser));
                        }, 20L);
                    }
                }, 5L);
            }

            // Webhook notification
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", jail.getPlayerName());
            placeholders.put("%releaser%", releaser);
            placeholders.put("%timestamp%", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date()));
            plugin.getWebhookService().sendCustom("unjail", placeholders);

            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get release location dari config
     */
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

    /**
     * Set release location
     */
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
     * Check if player is jailed
     */
    public boolean isJailed(UUID uuid) {
        return activeJails.containsKey(uuid);
    }

    /**
     * Get jail data for player
     */
    public Jail getJail(UUID uuid) {
        return activeJails.get(uuid);
    }

    /**
     * Increment labor progress
     */
    public void incrementLabor(UUID uuid) {
        plugin.getLogger().info("[Jail-Labor-Increment] Called for UUID: " + uuid);

        Jail jail = activeJails.get(uuid);
        if (jail == null) {
            plugin.getLogger().warning("[Jail-Labor-Increment] Jail not found in activeJails!");
            return;
        }

        int beforeProgress = jail.getLaborProgress();
        plugin.getLogger().info("[Jail-Labor-Increment] Progress before: " + beforeProgress);

        jail.incrementLabor();

        int afterProgress = jail.getLaborProgress();
        plugin.getLogger().info("[Jail-Labor-Increment] Progress after: " + afterProgress);

        // Update database
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE jails SET labor_progress = ? WHERE uuid = ?")) {

            pstmt.setInt(1, jail.getLaborProgress());
            pstmt.setString(2, uuid.toString());
            int rows = pstmt.executeUpdate();

            plugin.getLogger().info("[Jail-Labor-Increment] Database updated, rows affected: " + rows);

            // Check if can be released
            if (jail.canBeReleased()) {
                plugin.getLogger().info("[Jail-Labor-Increment] Jail can be released, calling releasePlayer");
                releasePlayer(uuid, "SYSTEM");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Jail-Labor-Increment] Database error!");
            e.printStackTrace();
        }
    }

    /**
     * Get cell location from config
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

    /**
     * Save cell location to config
     */
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

    /**
     * Get list of available cells
     */
    public Set<String> getCellNames() {
        if (!plugin.getConfig().contains("jail-system.cells")) {
            return Collections.emptySet();
        }
        return plugin.getConfig().getConfigurationSection("jail-system.cells").getKeys(false);
    }

    /**
     * Load active jails from database
     */
    private void loadActiveJails() {
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM jails");
             ResultSet rs = pstmt.executeQuery()) {

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

                // Check if jail expired
                if (jail.canBeReleased()) {
                    plugin.getLogger().info("[Jail] Auto-releasing " + jail.getPlayerName() + " (expired)");
                    releasePlayer(uuid, "SYSTEM");
                } else {
                    activeJails.put(uuid, jail);
                }
            }

            plugin.getLogger().info("Loaded " + activeJails.size() + " active jails");

            // Start periodic check for expired jails
            startExpirationTask();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start task to check expired jails every 5 seconds
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

            for (UUID uuid : toRelease) {
                Jail jail = activeJails.get(uuid);
                plugin.getLogger().info("[Jail] Auto-releasing " + jail.getPlayerName() + " (time expired)");
                releasePlayer(uuid, "SYSTEM");
            }
        }, 100L, 100L); // Check every 5 seconds
    }

    /**
     * Send jail notification to player
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

        // Webhook notification
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
