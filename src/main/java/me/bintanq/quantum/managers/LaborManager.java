package me.bintanq.quantum.managers;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;

public class LaborManager {
    private final QuantumPunish plugin;
    private final Map<String, List<Location>> spawnerLocations = new HashMap<>();
    private final Map<Location, Long> blockRespawnTimers = new HashMap<>();
    private int taskId = -1;

    public LaborManager(QuantumPunish plugin) {
        this.plugin = plugin;
        loadSpawnerLocations();
        startRespawnTask();
    }

    /**
     * Add spawner location for a cell
     */
    public void addSpawnerLocation(String cellName, Location location) {
        spawnerLocations.computeIfAbsent(cellName, k -> new ArrayList<>()).add(location);
        saveSpawnerLocations();
    }

    /**
     * Remove spawner location
     */
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

    /**
     * Get spawner locations for cell
     */
    public List<Location> getSpawnerLocations(String cellName) {
        return spawnerLocations.getOrDefault(cellName, Collections.emptyList());
    }

    /**
     * Schedule block respawn
     */
    public void scheduleRespawn(Location location) {
        long respawnDelay = plugin.getConfig().getLong("jail-system.labor.respawn-delay", 3000);
        blockRespawnTimers.put(location, System.currentTimeMillis() + respawnDelay);
    }

    /**
     * Check if location is a spawner
     */
    public boolean isSpawnerLocation(Location location) {
        for (List<Location> locs : spawnerLocations.values()) {
            for (Location spawnerLoc : locs) {
                // Check dengan tolerance 1 block
                if (spawnerLoc.getWorld().equals(location.getWorld()) &&
                        Math.abs(spawnerLoc.getBlockX() - location.getBlockX()) <= 0 &&
                        Math.abs(spawnerLoc.getBlockY() - location.getBlockY()) <= 0 &&
                        Math.abs(spawnerLoc.getBlockZ() - location.getBlockZ()) <= 0) {
                    plugin.getLogger().info("[Labor-Spawner] Location match found at " +
                            spawnerLoc.getBlockX() + "," + spawnerLoc.getBlockY() + "," + spawnerLoc.getBlockZ());
                    return true;
                }
            }
        }
        plugin.getLogger().info("[Labor-Spawner] No spawner match for " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        plugin.getLogger().info("[Labor-Spawner] Total spawner cells: " + spawnerLocations.size());
        return false;
    }

    /**
     * Get labor block material
     */
    public Material getLaborBlockMaterial() {
        String materialName = plugin.getConfig().getString("jail-system.labor.block-type", "COBBLESTONE");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid labor block material: " + materialName + ", using COBBLESTONE");
            return Material.COBBLESTONE;
        }
    }

    /**
     * Load spawner locations from config
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
                Block block = loc.getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(getLaborBlockMaterial());
                }
            }

            spawnerLocations.put(cellName, locations);
        }

        plugin.getLogger().info("Loaded " + spawnerLocations.size() + " labor spawner cells");
    }

    /**
     * Save spawner locations to config
     */
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
     * Start respawn task
     */
    private void startRespawnTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            Material blockMaterial = getLaborBlockMaterial();

            Iterator<Map.Entry<Location, Long>> iterator = blockRespawnTimers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Location, Long> entry = iterator.next();

                if (currentTime >= entry.getValue()) {
                    Location loc = entry.getKey();
                    Block block = loc.getBlock();

                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                        block.setType(blockMaterial);
                        plugin.getLogger().info("[Labor-Spawner] Respawned block at " +
                                loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                    }

                    iterator.remove();
                }
            }

            // Also check and place missing blocks at all spawner locations
            for (List<Location> locs : spawnerLocations.values()) {
                for (Location loc : locs) {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                        block.setType(blockMaterial);
                    }
                }
            }
        }, 20L, 20L); // Run every second

        plugin.getLogger().info("[Labor-Spawner] Respawn task started");
    }

    /**
     * Stop respawn task
     */
    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
