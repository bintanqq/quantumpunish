package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.*;

public class QInfoCommand extends BaseCommand {
    public QInfoCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "playerinfo";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.info")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.qinfo"));
            return true;
        }

        String playerName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        PlayerData data = plugin.getPlayerDataService().getPlayerData(target.getUniqueId());

        if (data == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-not-found"));
            return true;
        }

        int warnings = plugin.getWarningService().getWarningPoints(target.getUniqueId());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String divider = plugin.getMessageManager().getMessage("divider");
        sender.sendMessage(divider);
        sender.sendMessage(plugin.getMessageManager().getMessage("info-header")
                .replace("%player%", data.getLastName()));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-uuid")
                .replace("%uuid%", data.getUuid().toString()));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-warnings")
                .replace("%warnings%", String.valueOf(warnings)));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-first-join")
                .replace("%date%", sdf.format(new Date(data.getFirstJoin()))));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-last-seen")
                .replace("%date%", sdf.format(new Date(data.getLastSeen()))));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-ips")
                .replace("%ips%", data.getIpAddresses()));
        sender.sendMessage(divider);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            return players;
        }
        return null;
    }
}