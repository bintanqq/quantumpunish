package me.bintanq.quantum.managers;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Punishment;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MessageManager {
    private final QuantumPunish plugin;
    private final FileConfiguration messages;

    public MessageManager(QuantumPunish plugin, FileConfiguration messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public String getMessage(String path) {
        String fullPath = "messages." + path;
        String message = messages.getString(fullPath, "&cMessage not found: " + fullPath);
        return colorize(message);
    }

    public List<String> getMessagesList(String path) {
        String fullPath = "messages." + path;

        List<String> messageList = messages.getStringList(fullPath);

        if (messageList.isEmpty()) {
            String errorMessage = colorize("&c[ERROR] Message List not found at: " + fullPath);
            return Collections.singletonList(errorMessage);
        }

        return messageList.stream()
                .map(this::colorize)
                .collect(Collectors.toList());
    }

    public String getPunishmentBroadcast(Punishment punishment) {
        String divider = getMessage("divider");
        String type = punishment.getType().name().toLowerCase();

        if ((type.equals("mute") || type.equals("ban")) && punishment.getExpires() != null) {
            type = "temp" + type;
        }

        String mainMessage = getMessage("broadcast." + type)
                .replace("%player%", punishment.getPlayerName())
                .replace("%staff%", punishment.getStaff())
                .replace("%reason%", punishment.getReason());

        if (punishment.getExpires() != null) {
            mainMessage += "\n" + getMessage("broadcast.duration")
                    .replace("%duration%", plugin.getPunishmentService()
                            .formatDuration(punishment.getExpires() - punishment.getTimestamp()));
        }

        return divider + "\n" + mainMessage + "\n" + divider;
    }

    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}