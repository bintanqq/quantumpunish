package me.bintanq.quantum.listeners;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFilterListener implements Listener {
    private final QuantumPunish plugin;

    public ChatFilterListener(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("quantumpunish.bypass.filter")) {
            return;
        }

        if (plugin.getPunishmentService().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("currently-muted"));
            return;
        }

        String filtered = plugin.getChatFilterService().filterMessage(event.getMessage(), player);

        if (filtered == null) {
            event.setCancelled(true);
        } else if (!filtered.equals(event.getMessage())) {
            event.setMessage(filtered);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().trim();
        String lower = msg.toLowerCase();
        if (lower.startsWith("/essentials:unmute ") || lower.startsWith("/eunmute ")) {
            String[] parts = msg.split("\\s+");
            if (parts.length >= 2) {
                String targetName = parts[1];
                org.bukkit.entity.Player staff = event.getPlayer();
                plugin.getPunishmentService().unmutePlayer(targetName, null, staff.getName());
            }
        }
    }
}