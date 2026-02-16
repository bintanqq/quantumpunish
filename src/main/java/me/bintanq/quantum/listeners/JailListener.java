package me.bintanq.quantum.listeners;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Jail;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class JailListener implements Listener {
    private final QuantumPunish plugin;
    private final Set<UUID> recentlyTeleported = new HashSet<>();

    public JailListener(QuantumPunish plugin) {
        this.plugin = plugin;
        startActionBarTask();
    }

    /**
     * Handle block breaking for labor system
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getLogger().info("[Jail-Labor-Debug] Player " + player.getName() + " broke a block");
        plugin.getLogger().info("[Jail-Labor-Debug] Is jailed: " + plugin.getJailService().isJailed(uuid));

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        plugin.getLogger().info("[Jail-Labor-Debug] Player IS jailed, processing...");

        // Jailed player trying to break block
        Jail jail = plugin.getJailService().getJail(uuid);

        if (jail == null) {
            plugin.getLogger().warning("[Jail-Labor-Debug] Jail object is NULL!");
            return;
        }

        plugin.getLogger().info("[Jail-Labor-Debug] Jail found: Cell=" + jail.getCellName() +
                ", Labor=" + jail.getLaborProgress() + "/" + jail.getLaborRequired());

        Block block = event.getBlock();
        Location blockLoc = block.getLocation();

        plugin.getLogger().info("[Jail-Labor-Debug] Block location: " +
                blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ());

        // Check if labor is enabled
        boolean laborEnabled = plugin.getConfig().getBoolean("jail-system.labor.enabled", false);
        plugin.getLogger().info("[Jail-Labor-Debug] Labor enabled in config: " + laborEnabled);

        if (!laborEnabled) {
            event.setCancelled(true);
            plugin.getLogger().info("[Jail-Labor-Debug] Labor not enabled, cancelling");
            return;
        }

        if (jail.getLaborRequired() <= 0) {
            event.setCancelled(true);
            plugin.getLogger().info("[Jail-Labor-Debug] No labor required for this jail, cancelling");
            return;
        }

        Material laborMaterial = plugin.getLaborManager().getLaborBlockMaterial();

        plugin.getLogger().info("[Jail-Labor-Debug] Block type: " + block.getType() + ", Required: " + laborMaterial);

        // Check block type
        if (block.getType() != laborMaterial) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-wrong-block")
                    .replace("%block%", laborMaterial.name()));
            plugin.getLogger().info("[Jail-Labor-Debug] Wrong block type, cancelling");
            return;
        }

        // Check spawner location
        boolean isSpawner = plugin.getLaborManager().isSpawnerLocation(blockLoc);
        plugin.getLogger().info("[Jail-Labor-Debug] Is spawner location: " + isSpawner);

        if (!isSpawner) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-wrong-location"));
            plugin.getLogger().info("[Jail-Labor-Debug] Not a spawner location, cancelling");
            return;
        }

        // ALLOW breaking by un-cancelling if it was cancelled by other plugins
        event.setCancelled(false);

        plugin.getLogger().info("[Jail-Labor-Debug] ALL CHECKS PASSED! Processing labor...");

        // Prevent drops
        event.setDropItems(false);
        event.setExpToDrop(0);

        // Increment labor progress
        plugin.getLogger().info("[Jail-Labor-Debug] Calling incrementLabor...");
        plugin.getJailService().incrementLabor(uuid);
        plugin.getLogger().info("[Jail-Labor-Debug] incrementLabor called");

        // Schedule respawn
        plugin.getLaborManager().scheduleRespawn(blockLoc);

        // Sound feedback
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

        // Send progress message - get FRESH data
        Jail updatedJail = plugin.getJailService().getJail(uuid);
        if (updatedJail != null) {
            int progress = updatedJail.getLaborProgress();
            int required = updatedJail.getLaborRequired();

            plugin.getLogger().info("[Jail-Labor-Debug] After increment: " + progress + "/" + required);

            player.sendMessage(plugin.getMessageManager().colorize(
                    "&7[&cLabor&7] &fProgress: &a" + progress + "&7/&e" + required +
                            " &7(&a" + (progress * 100 / required) + "%&7)"));

            // Check if completed
            if (progress >= required) {
                player.sendMessage(plugin.getMessageManager().getMessage("jail-labor-complete"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                plugin.getLogger().info("[Jail-Labor-Debug] Labor complete!");
            }
        } else {
            plugin.getLogger().warning("[Jail-Labor-Debug] Updated jail is NULL after increment!");
        }
    }

    /**
     * Anti-escape: Prevent teleportation
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        // Allow plugin-initiated teleports
        if (recentlyTeleported.contains(uuid)) {
            recentlyTeleported.remove(uuid);
            return;
        }

        // Block all other teleports
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-no-teleport"));
        }
    }

    /**
     * Anti-escape: Radius check
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Optimize: Only check if player actually moved blocks
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        Jail jail = plugin.getJailService().getJail(uuid);
        Location cellLocation = plugin.getJailService().getCellLocation(jail.getCellName());

        if (cellLocation == null) {
            plugin.getLogger().warning("[Jail] Cell location not found for " + jail.getCellName());
            return;
        }

        double radius = plugin.getConfig().getDouble("jail-system.escape-radius", 10.0);
        double distance = event.getTo().distance(cellLocation);

        plugin.getLogger().info("[Jail-Escape] " + player.getName() + " distance from cell: " + String.format("%.2f", distance) + " (max: " + radius + ")");

        if (distance > radius) {
            event.setCancelled(true);

            // Teleport back to cell location (NOT center, to actual cell spawn point)
            recentlyTeleported.add(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(cellLocation);
                player.sendMessage(plugin.getMessageManager().getMessage("jail-escape-attempt"));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                plugin.getLogger().info("[Jail-Escape] " + player.getName() + " teleported back to cell");
            });

            // Remove from set after a tick
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                recentlyTeleported.remove(uuid);
            }, 2L);
        }
    }

    /**
     * Command restriction
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        String command = event.getMessage().toLowerCase().split(" ")[0];

        // Allowed commands
        Set<String> allowedCommands = new HashSet<>(
                plugin.getConfig().getStringList("jail-system.allowed-commands")
        );
        allowedCommands.add("/jailstatus");
        allowedCommands.add("/msg");
        allowedCommands.add("/tell");
        allowedCommands.add("/w");

        boolean allowed = false;
        for (String allowedCmd : allowedCommands) {
            if (command.startsWith(allowedCmd.toLowerCase())) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-command-blocked"));
        }
    }

    /**
     * Chat restriction (if enabled)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        boolean allowChat = plugin.getConfig().getBoolean("jail-system.allow-chat", false);

        if (!allowChat) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-chat-blocked"));
        }
    }

    /**
     * Prevent item interactions
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (plugin.getJailService().isJailed(player.getUniqueId())) {
            if (!plugin.getConfig().getBoolean("jail-system.allow-interactions", false)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Teleport jailed players to their cell on join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        Jail jail = plugin.getJailService().getJail(uuid);
        if (jail == null) return;

        // Check if jail expired
        if (jail.canBeReleased()) {
            plugin.getJailService().releasePlayer(uuid, "SYSTEM");
            return;
        }

        // Teleport to jail cell
        Location cellLocation = plugin.getJailService().getCellLocation(jail.getCellName());
        if (cellLocation != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(cellLocation);
                    player.sendMessage(plugin.getMessageManager().getMessage("jail-teleported-on-join")
                            .replace("%cell%", jail.getCellName()));
                    plugin.getLogger().info("[Jail] " + player.getName() + " teleported to jail on join");
                }
            }, 20L); // Delay 1 detik untuk memastikan player fully loaded
        }
    }

    /**
     * Prevent dropping items
     */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getJailService().isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("jail-no-drop"));
        }
    }

    /**
     * Prevent picking up items
     */
    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (plugin.getJailService().isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Start action bar task to show progress
     */
    private void startActionBarTask() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                if (!plugin.getJailService().isJailed(uuid)) {
                    continue;
                }

                Jail jail = plugin.getJailService().getJail(uuid);
                if (jail == null) continue;

                // Build action bar message
                StringBuilder actionBar = new StringBuilder();

                // Labor progress
                if (jail.getLaborRequired() > 0) {
                    int progress = jail.getLaborProgress();
                    int required = jail.getLaborRequired();
                    int percentage = (int) ((progress / (double) required) * 100);

                    actionBar.append("§c§lLabor: §f")
                            .append(progress)
                            .append("§7/§f")
                            .append(required)
                            .append(" §7(§e")
                            .append(percentage)
                            .append("%§7)");

                    // Progress bar
                    actionBar.append(" §8[");
                    int bars = 20;
                    int filled = (progress * bars) / required;
                    for (int i = 0; i < bars; i++) {
                        if (i < filled) {
                            actionBar.append("§a▌");
                        } else {
                            actionBar.append("§7▌");
                        }
                    }
                    actionBar.append("§8]");
                }

                // Time remaining
                long remaining = jail.getExpires() - System.currentTimeMillis();
                if (remaining > 0) {
                    if (actionBar.length() > 0) actionBar.append(" §8| ");
                    actionBar.append("§c§lTime: §f").append(formatTime(remaining));
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(actionBar.toString()));
            }
        }, 0L, 20L); // Update every second
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return seconds + "s";
        }
    }
}
