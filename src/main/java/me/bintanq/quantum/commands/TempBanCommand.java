package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class TempBanCommand extends BaseCommand {
    public TempBanCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "tempban";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.tempban")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.tempban"));
            return true;
        }

        String playerName = args[0];
        String reason = args[1];
        String durationStr = args[2];

        long duration = plugin.getPunishmentService().parseDuration(durationStr);
        if (duration <= 0) {
            sender.sendMessage(plugin.getMessageManager().getMessage("invalid-duration"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        long expires = System.currentTimeMillis() + duration;

        plugin.getPunishmentService().banPlayer(
                playerName,
                target.getUniqueId(),
                sender.getName(),
                reason,
                expires,
                null
        );

        sender.sendMessage(plugin.getMessageManager().getMessage("tempban-success")
                .replace("%player%", playerName)
                .replace("%reason%", reason)
                .replace("%duration%", plugin.getPunishmentService().formatDuration(duration)));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            return players;
        }
        if (args.length == 3) {
            return Arrays.asList("10m", "30m", "1h", "6h", "1d", "7d", "30d");
        }
        return null;
    }
}