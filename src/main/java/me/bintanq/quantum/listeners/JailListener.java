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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * JailListener - Production Version with Hologram Lifecycle
 * ============================================================================
 */
public class JailListener implements Listener {
    private final QuantumPunish plugin;
    private final Set<UUID> recentlyTeleported = Collections.synchronizedSet(new HashSet<>());
    private final Map<Location, Long> playerPlacedBlocks = new ConcurrentHashMap<>();

    public JailListener(QuantumPunish plugin) {
        this.plugin = plugin;
        startActionBarTask();
        startBlockCleanupTask();
    }

    /**
     * ============================================================================
     * INTERACTION HANDLER - Labor Block Bypass
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getJailService().isJailed(player.getUniqueId())) {
            return;
        }

        boolean allowInteractions = plugin.getConfig().getBoolean("jail-system.allow-interactions", false);

        if (allowInteractions) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            event.setCancelled(true);
            return;
        }

        if (isLaborBlockAtSpawner(clickedBlock)) {
            return;
        }

        event.setCancelled(true);
    }

    /**
     * ============================================================================
     * BLOCK BREAK HANDLER - Async Labor Processing
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        Jail jail = plugin.getJailService().getJail(uuid);
        if (jail == null) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().colorize("&cJail data error! Contact admin."));
            return;
        }

        Block block = event.getBlock();
        Location blockLoc = block.getLocation();

        boolean laborEnabled = plugin.getConfig().getBoolean("jail-system.labor.enabled", false);
        if (!laborEnabled) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-labor-disabled"));
            return;
        }

        if (jail.getLaborRequired() <= 0) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().colorize(
                    "&cYou cannot break blocks while jailed!"));
            return;
        }

        if (!validateLaborBlock(player, block, blockLoc, jail.getCellName())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);
        event.setDropItems(false);
        event.setExpToDrop(0);

        block.setType(Material.AIR);

        processLaborAsync(player, uuid, blockLoc, jail);
    }

    /**
     * ============================================================================
     * BLOCK PLACE HANDLER - Anti-Placement
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getJailService().isJailed(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().colorize(
                "&cYou cannot place blocks while jailed!"));

        Location attemptLoc = event.getBlock().getLocation();
        playerPlacedBlocks.put(attemptLoc, System.currentTimeMillis());
    }

    /**
     * ============================================================================
     * ASYNC LABOR PROCESSOR
     * ============================================================================
     */
    private void processLaborAsync(Player player, UUID uuid, Location blockLoc, Jail jail) {
        final String playerName = player.getName();
        final int oldProgress = jail.getLaborProgress();
        final int required = jail.getLaborRequired();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getJailService().incrementLabor(uuid);

                Jail updatedJail = plugin.getJailService().getJail(uuid);
                if (updatedJail == null) {
                    plugin.getLogger().severe("[Jail] Failed to get updated jail data for " + playerName);
                    return;
                }

                final int newProgress = updatedJail.getLaborProgress();
                final int percentage = required > 0 ? Math.min(100, (newProgress * 100 / required)) : 100;
                final boolean isComplete = newProgress >= required;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                        player.sendMessage(plugin.getMessageManager().colorize(
                                "&7[&cLabor&7] &fProgress: &a" + newProgress + "&7/&e" + required +
                                        " &7(&a" + percentage + "%&7)"));

                        if (isComplete) {
                            player.sendMessage(plugin.getMessageManager().getMessage("jail-labor-complete"));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        }
                    }

                    // ═══════════════════════════════════════════════════════════
                    // HOLOGRAM: Update IMMEDIATELY after increment (sync thread)
                    // ═══════════════════════════════════════════════════════════
                    plugin.getLaborManager().updateHologramForPlayer(uuid, blockLoc);

                    scheduleBlockRespawn(blockLoc, uuid);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("[Jail] Error processing labor for " + playerName);
                e.printStackTrace();
            }
        });
    }

    /**
     * ============================================================================
     * BLOCK RESPAWN
     * ============================================================================
     */
    private void scheduleBlockRespawn(Location blockLoc, UUID playerUuid) {
        long respawnDelayMillis = plugin.getConfig().getLong("jail-system.labor.respawn-delay", 3000);
        long respawnDelayTicks = respawnDelayMillis / 50;
        Material laborMaterial = plugin.getLaborManager().getLaborBlockMaterial();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block block = blockLoc.getBlock();

            if (block.getType() == Material.AIR ||
                    block.getType() == Material.CAVE_AIR ||
                    block.getType() == Material.VOID_AIR) {

                block.setType(laborMaterial);

                // ═══════════════════════════════════════════════════════════
                // HOLOGRAM: Recreate after respawn
                // ═══════════════════════════════════════════════════════════
                plugin.getLaborManager().createHologramForPlayer(blockLoc, playerUuid);
            }
        }, respawnDelayTicks);
    }

    /**
     * ============================================================================
     * VALIDATORS
     * ============================================================================
     */
    private boolean validateLaborBlock(Player player, Block block, Location blockLoc, String cellName) {
        Material laborMaterial = plugin.getLaborManager().getLaborBlockMaterial();
        if (block.getType() != laborMaterial) {
            player.sendMessage(plugin.getMessageManager().getMessage("jail-wrong-block")
                    .replace("%block%", laborMaterial.name()));
            return false;
        }

        if (!isSpawnerLocation(blockLoc, cellName)) {
            player.sendMessage(plugin.getMessageManager().getMessage("jail-wrong-location"));
            return false;
        }

        if (playerPlacedBlocks.containsKey(blockLoc)) {
            player.sendMessage(plugin.getMessageManager().getMessage("jail-block-place"));
            return false;
        }

        return true;
    }

    private boolean isLaborBlockAtSpawner(Block block) {
        Material laborMaterial = plugin.getLaborManager().getLaborBlockMaterial();
        if (block.getType() != laborMaterial) {
            return false;
        }
        return plugin.getLaborManager().isSpawnerLocation(block.getLocation());
    }

    private boolean isSpawnerLocation(Location blockLoc, String cellName) {
        List<Location> spawners = plugin.getLaborManager().getSpawnerLocations(cellName);

        int blockX = blockLoc.getBlockX();
        int blockY = blockLoc.getBlockY();
        int blockZ = blockLoc.getBlockZ();
        String blockWorld = blockLoc.getWorld().getName();

        for (Location spawner : spawners) {
            if (blockWorld.equals(spawner.getWorld().getName()) &&
                    blockX == spawner.getBlockX() &&
                    blockY == spawner.getBlockY() &&
                    blockZ == spawner.getBlockZ()) {
                return true;
            }
        }

        return false;
    }

    /**
     * ============================================================================
     * CLEANUP TASK
     * ============================================================================
     */
    private void startBlockCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            playerPlacedBlocks.entrySet().removeIf(entry -> {
                Block block = entry.getKey().getBlock();
                return block.getType() == Material.AIR ||
                        block.getType() == Material.CAVE_AIR ||
                        currentTime - entry.getValue() > 300000; // 5 minutes
            });
        }, 6000L, 6000L);
    }

    /**
     * ============================================================================
     * TELEPORTATION HANDLER
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        if (recentlyTeleported.contains(uuid)) {
            recentlyTeleported.remove(uuid);
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(plugin.getMessageManager().getMessage("jail-no-teleport"));
    }

    /**
     * ============================================================================
     * MOVEMENT HANDLER
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
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
        if (jail == null) return;

        Location cellLocation = plugin.getJailService().getCellLocation(jail.getCellName());
        if (cellLocation == null) return;

        double radius = plugin.getConfig().getDouble("jail-system.escape-radius", 10.0);
        double distance = event.getTo().distance(cellLocation);

        if (distance > radius) {
            event.setCancelled(true);
            recentlyTeleported.add(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(cellLocation);
                player.sendMessage(plugin.getMessageManager().getMessage("jail-escape-attempt"));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    recentlyTeleported.remove(uuid);
                }, 2L);
            });
        }
    }

    /**
     * ============================================================================
     * COMMAND RESTRICTION
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getJailService().isJailed(player.getUniqueId())) {
            return;
        }

        String command = event.getMessage().toLowerCase().split(" ")[0];

        Set<String> allowedCommands = new HashSet<>(
                plugin.getConfig().getStringList("jail-system.allowed-commands")
        );
        allowedCommands.add("/jailstatus");
        allowedCommands.add("/msg");
        allowedCommands.add("/tell");
        allowedCommands.add("/w");
        allowedCommands.add("/reply");
        allowedCommands.add("/r");

        boolean allowed = allowedCommands.stream()
                .anyMatch(cmd -> command.startsWith(cmd.toLowerCase()));

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-command-blocked"));
        }
    }

    /**
     * ============================================================================
     * CHAT RESTRICTION
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getJailService().isJailed(player.getUniqueId())) {
            return;
        }

        boolean allowChat = plugin.getConfig().getBoolean("jail-system.allow-chat", false);
        if (!allowChat) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("jail-chat-blocked"));
        }
    }

    /**
     * ============================================================================
     * PLAYER JOIN HANDLER
     * ============================================================================
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

        if (jail.canBeReleased()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getJailService().releasePlayer(uuid, "SYSTEM");
            });
            return;
        }

        Location cellLocation = plugin.getJailService().getCellLocation(jail.getCellName());
        if (cellLocation != null) {
            recentlyTeleported.add(uuid);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(cellLocation);
                    player.sendMessage(plugin.getMessageManager().getMessage("jail-teleported-on-join")
                            .replace("%cell%", jail.getCellName()));

                    // ═══════════════════════════════════════════════════════════
                    // HOLOGRAM: Create all holograms for player on join
                    // ═══════════════════════════════════════════════════════════
                    plugin.getLaborManager().createAllHologramsForPlayer(uuid, jail.getCellName());

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        recentlyTeleported.remove(uuid);
                    }, 2L);
                }
            }, 20L);
        }
    }

    /**
     * ============================================================================
     * PLAYER QUIT HANDLER - Hologram Cleanup
     * ============================================================================
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getJailService().isJailed(uuid)) {
            return;
        }

        // ═══════════════════════════════════════════════════════════════════
        // HOLOGRAM: Remove all holograms when player logs out
        // ═══════════════════════════════════════════════════════════════════
        plugin.getLaborManager().removeAllHologramsForPlayer(uuid);
    }

    /**
     * ============================================================================
     * ITEM DROP/PICKUP RESTRICTION
     * ============================================================================
     */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getJailService().isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("jail-no-drop"));
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (plugin.getJailService().isJailed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * ============================================================================
     * ACTION BAR TASK
     * ============================================================================
     */
    private void startActionBarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                if (!plugin.getJailService().isJailed(uuid)) {
                    continue;
                }

                Jail jail = plugin.getJailService().getJail(uuid);
                if (jail == null) continue;

                StringBuilder actionBar = new StringBuilder();

                if (jail.getLaborRequired() > 0) {
                    int progress = jail.getLaborProgress();
                    int required = jail.getLaborRequired();
                    int percentage = Math.min(100, (progress * 100) / required);

                    actionBar.append("§c§lLabor: §f")
                            .append(progress)
                            .append("§7/§f")
                            .append(required)
                            .append(" §7(§e")
                            .append(percentage)
                            .append("%§7) §8[");

                    int bars = 20;
                    int filled = (progress * bars) / required;
                    for (int i = 0; i < bars; i++) {
                        actionBar.append(i < filled ? "§a▌" : "§7▌");
                    }
                    actionBar.append("§8]");
                }

                long remaining = jail.getExpires() - System.currentTimeMillis();
                if (remaining > 0) {
                    if (actionBar.length() > 0) actionBar.append(" §8| ");
                    actionBar.append("§c§lTime: §f").append(formatTime(remaining));
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(actionBar.toString()));
            }
        }, 0L, 20L);
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