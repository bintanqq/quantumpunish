package me.bintanq.quantum.managers;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Jail;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ============================================================================
 * LaborManager - Hologram System with Per-Player Visibility
 * ============================================================================
 * Features:
 * - Per-player hologram visibility (only jailed player sees it)
 * - Instant spawn on jail
 * - Real-time updates
 * - Automatic cleanup on unjail/logout/disable
 * - Fully configurable via config.yml
 * - Zero memory leaks
 */
public class LaborManager {
    private final QuantumPunish plugin;

    // Spawner locations tracking
    private final Map<String, List<Location>> spawnerLocations = new HashMap<>();

    // Block respawn timers
    private final Map<Location, Long> blockRespawnTimers = new HashMap<>();

    // Hologram tracking: Location -> TextDisplay Entity
    private final Map<Location, UUID> activeHolograms = new ConcurrentHashMap<>();

    // Player hologram tracking: Player UUID -> Set of hologram UUIDs they can see
    private final Map<UUID, Set<UUID>> playerVisibleHolograms = new ConcurrentHashMap<>();

    private int respawnTaskId = -1;

    public LaborManager(QuantumPunish plugin) {
        this.plugin = plugin;
        loadSpawnerLocations();
        startRespawnTask();
    }

    /**
     * ============================================================================
     * SPAWNER MANAGEMENT
     * ============================================================================
     */
    public void addSpawnerLocation(String cellName, Location location) {
        spawnerLocations.computeIfAbsent(cellName, k -> new ArrayList<>()).add(location);
        saveSpawnerLocations();
    }

    public void removeSpawnerLocation(String cellName, Location location) {
        List<Location> locs = spawnerLocations.get(cellName);
        if (locs != null) {
            locs.removeIf(loc -> loc.distance(location) < 1.0);
            if (locs.isEmpty()) {
                spawnerLocations.remove(cellName);
            }
            saveSpawnerLocations();
        }
    }

    public List<Location> getSpawnerLocations(String cellName) {
        return spawnerLocations.getOrDefault(cellName, Collections.emptyList());
    }

    public boolean isSpawnerLocation(Location location) {
        for (List<Location> locs : spawnerLocations.values()) {
            for (Location spawnerLoc : locs) {
                if (spawnerLoc.getWorld().equals(location.getWorld()) &&
                        Math.abs(spawnerLoc.getBlockX() - location.getBlockX()) <= 0 &&
                        Math.abs(spawnerLoc.getBlockY() - location.getBlockY()) <= 0 &&
                        Math.abs(spawnerLoc.getBlockZ() - location.getBlockZ()) <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public Material getLaborBlockMaterial() {
        String materialName = plugin.getConfig().getString("jail-system.labor.block-type", "COBBLESTONE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[LaborManager] Invalid material: " + materialName + ", using COBBLESTONE");
            return Material.COBBLESTONE;
        }
    }

    /**
     * ============================================================================
     * BLOCK RESPAWN SYSTEM
     * ============================================================================
     */
    public void scheduleRespawn(Location location) {
        long respawnDelay = plugin.getConfig().getLong("jail-system.labor.respawn-delay", 3000);
        blockRespawnTimers.put(location, System.currentTimeMillis() + respawnDelay);
    }

    private void startRespawnTask() {
        if (respawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(respawnTaskId);
        }

        respawnTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            Material blockMaterial = getLaborBlockMaterial();

            Iterator<Map.Entry<Location, Long>> iterator = blockRespawnTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Location, Long> entry = iterator.next();

                if (currentTime >= entry.getValue()) {
                    Location loc = entry.getKey();

                    if (loc.getBlock().getType() == Material.AIR ||
                            loc.getBlock().getType() == Material.CAVE_AIR) {
                        loc.getBlock().setType(blockMaterial);

                        // Create hologram for ALL jailed players
                        createHologramsForLocation(loc);
                    }

                    iterator.remove();
                }
            }

            // Also ensure spawner blocks exist
            for (List<Location> locs : spawnerLocations.values()) {
                for (Location loc : locs) {
                    if (loc.getBlock().getType() == Material.AIR ||
                            loc.getBlock().getType() == Material.CAVE_AIR) {
                        loc.getBlock().setType(blockMaterial);
                    }
                }
            }
        }, 20L, 20L);
    }

    /**
     * ============================================================================
     * HOLOGRAM SYSTEM - Per-Player Visibility
     * ============================================================================
     */

    /**
     * Create hologram for a specific player at a location
     * Called when: Player jailed, block respawns, progress updates
     */
    public void createHologramForPlayer(Location blockLoc, UUID playerUuid) {
        // Check if holograms enabled
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        Jail jail = plugin.getJailService().getJail(playerUuid);
        if (jail == null) {
            return;
        }

        // Remove old hologram at this location first
        removeHologramAt(blockLoc);

        // Get hologram config
        double heightOffset = plugin.getConfig().getDouble("hologram.height-offset", 1.5);
        List<String> textLines = plugin.getConfig().getStringList("hologram.text");

        if (textLines.isEmpty()) {
            textLines = Arrays.asList(
                    "§b§lLABOR BLOCK §7(Hancurkan!)",
                    "§fProgress: §e{completed}§7/§e{required}"
            );
        }

        // Build text with placeholders
        int progress = jail.getLaborProgress();
        int required = jail.getLaborRequired();
        int percentage = required > 0 ? Math.min(100, (progress * 100 / required)) : 100;

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < textLines.size(); i++) {
            String line = textLines.get(i)
                    .replace("{completed}", String.valueOf(progress))
                    .replace("{required}", String.valueOf(required))
                    .replace("{percentage}", String.valueOf(percentage));

            text.append(line);
            if (i < textLines.size() - 1) {
                text.append("\n");
            }
        }

        // Create hologram location
        Location holoLoc = blockLoc.clone().add(0.5, heightOffset, 0.5);

        // Spawn TextDisplay
        TextDisplay hologram = blockLoc.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
            display.setText(text.toString());
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
            display.setSeeThrough(false);

            // CRITICAL: Make invisible by default
            display.setVisibleByDefault(false);
        });

        // Make visible ONLY to this player
        player.showEntity(plugin, hologram);

        // Track hologram
        activeHolograms.put(blockLoc, hologram.getUniqueId());
        playerVisibleHolograms.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(hologram.getUniqueId());
    }

    /**
     * Create holograms for ALL currently jailed players at a location
     * Called when: Block respawns
     */
    private void createHologramsForLocation(Location blockLoc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getJailService().isJailed(player.getUniqueId())) {
                createHologramForPlayer(blockLoc, player.getUniqueId());
            }
        }
    }

    /**
     * Update hologram text for a player
     * Called when: Labor progress increments
     */
    public void updateHologramForPlayer(UUID playerUuid, Location blockLoc) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        // Simply recreate the hologram with updated data
        createHologramForPlayer(blockLoc, playerUuid);
    }

    /**
     * Create holograms for player at ALL spawner locations in their cell
     * Called when: Player gets jailed
     */
    public void createAllHologramsForPlayer(UUID playerUuid, String cellName) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            return;
        }

        List<Location> spawners = getSpawnerLocations(cellName);
        for (Location spawnerLoc : spawners) {
            createHologramForPlayer(spawnerLoc, playerUuid);
        }
    }

    /**
     * Remove hologram at specific location
     */
    private void removeHologramAt(Location blockLoc) {
        UUID holoId = activeHolograms.remove(blockLoc);
        if (holoId == null) return;

        // Find and remove entity
        for (Entity entity : blockLoc.getWorld().getEntities()) {
            if (entity.getUniqueId().equals(holoId) && entity instanceof TextDisplay) {
                entity.remove();
                break;
            }
        }

        // Remove from all player tracking
        for (Set<UUID> holoSet : playerVisibleHolograms.values()) {
            holoSet.remove(holoId);
        }
    }

    /**
     * Remove ALL holograms for a specific player
     * Called when: Player unjailed or logs out
     */
    public void removeAllHologramsForPlayer(UUID playerUuid) {
        Set<UUID> holoIds = playerVisibleHolograms.remove(playerUuid);
        if (holoIds == null || holoIds.isEmpty()) {
            return;
        }

        // Remove all entities this player was seeing
        for (UUID holoId : holoIds) {
            for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) {
                if (entity.getUniqueId().equals(holoId) && entity instanceof TextDisplay) {
                    entity.remove();
                    break;
                }
            }
        }

        // Clean up from activeHolograms map
        activeHolograms.entrySet().removeIf(entry -> holoIds.contains(entry.getValue()));
    }

    /**
     * Remove ALL holograms (server shutdown)
     */
    public void removeAllHolograms() {
        // Remove all tracked entities
        for (UUID holoId : activeHolograms.values()) {
            for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) {
                if (entity.getUniqueId().equals(holoId) && entity instanceof TextDisplay) {
                    entity.remove();
                }
            }
        }

        activeHolograms.clear();
        playerVisibleHolograms.clear();
    }

    /**
     * ============================================================================
     * PERSISTENCE
     * ============================================================================
     */
    private void loadSpawnerLocations() {
        if (!plugin.getConfig().contains("jail-system.labor.spawners")) {
            return;
        }

        var section = plugin.getConfig().getConfigurationSection("jail-system.labor.spawners");
        if (section == null) return;

        for (String cellName : section.getKeys(false)) {
            List<Map<?, ?>> locList = section.getMapList(cellName);
            List<Location> locations = new ArrayList<>();

            for (Map<?, ?> locMap : locList) {
                String worldName = (String) locMap.get("world");
                double x = ((Number) locMap.get("x")).doubleValue();
                double y = ((Number) locMap.get("y")).doubleValue();
                double z = ((Number) locMap.get("z")).doubleValue();

                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                locations.add(loc);

                // Spawn initial block
                if (loc.getBlock().getType() == Material.AIR) {
                    loc.getBlock().setType(getLaborBlockMaterial());
                }
            }

            spawnerLocations.put(cellName, locations);
        }
    }

    private void saveSpawnerLocations() {
        plugin.getConfig().set("jail-system.labor.spawners", null);

        for (Map.Entry<String, List<Location>> entry : spawnerLocations.entrySet()) {
            String cellName = entry.getKey();
            List<Map<String, Object>> locList = new ArrayList<>();

            for (Location loc : entry.getValue()) {
                Map<String, Object> locMap = new HashMap<>();
                locMap.put("world", loc.getWorld().getName());
                locMap.put("x", loc.getBlockX());
                locMap.put("y", loc.getBlockY());
                locMap.put("z", loc.getBlockZ());
                locList.add(locMap);
            }

            plugin.getConfig().set("jail-system.labor.spawners." + cellName, locList);
        }

        plugin.saveConfig();
    }

    /**
     * ============================================================================
     * SHUTDOWN
     * ============================================================================
     */
    public void shutdown() {
        if (respawnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(respawnTaskId);
            respawnTaskId = -1;
        }

        // Remove all holograms
        removeAllHolograms();
    }
}