package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class TempMuteCommand extends BaseCommand {
    public TempMuteCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "tempmute";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.tempmute")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.tempmute"));
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

        plugin.getPunishmentService().mutePlayer(
                playerName,
                target.getUniqueId(),
                sender.getName(),
                reason,
                expires
        );

        sender.sendMessage(plugin.getMessageManager().getMessage("tempmute-success")
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
        if (args.length == 2) {
            return List.of("<reason>");
        }
        if (args.length == 3) {
            return Arrays.asList("5m", "10m", "30m", "1h", "6h", "12h", "1d");
        }
        return null;
    }
}