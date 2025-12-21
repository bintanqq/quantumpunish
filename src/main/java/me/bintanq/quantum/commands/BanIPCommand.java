package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class BanIPCommand extends BaseCommand {
    public BanIPCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "banip";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.banip")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.banip"));
            return true;
        }

        String playerName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-not-online"));
            return true;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        plugin.getPunishmentService().banIP(ip, sender.getName(), reason);

        sender.sendMessage(plugin.getMessageManager().getMessage("banip-success")
                .replace("%player%", playerName)
                .replace("%ip%", ip)
                .replace("%reason%", reason));

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
        return null;
    }
}