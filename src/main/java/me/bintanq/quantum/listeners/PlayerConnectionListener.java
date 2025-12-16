package me.bintanq.quantum.listeners;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit; // Import Bukkit
import org.bukkit.entity.Player; // Import Player
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    private final QuantumPunish plugin;

    public PlayerConnectionListener(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getPlayerDataService().updatePlayerData(uuid, name, ip);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getPlayerDataService().updatePlayerData(uuid, name, ip);
        });
    }
}