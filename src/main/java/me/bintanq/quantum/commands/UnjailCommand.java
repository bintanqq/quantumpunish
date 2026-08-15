package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class UnjailCommand extends BaseCommand {
    public UnjailCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "unjail";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.unjail")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (plugin.getJailService() == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-system-disabled"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.unjail"));
            return true;
        }

        String playerName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!plugin.getJailService().isJailed(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-not-jailed")
                    .replace("%player%", playerName));
            return true;
        }

        boolean success = plugin.getJailService().releasePlayer(target.getUniqueId(), sender.getName());

        if (success) {
            sender.sendMessage(plugin.getMessageManager().getMessage("unjail-success")
                    .replace("%player%", playerName));
        } else {
            sender.sendMessage(plugin.getMessageManager().getMessage("unjail-failed"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (plugin.getJailService() == null) return Collections.emptyList();

        if (args.length == 1) {
            Set<String> jailedPlayers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            jailedPlayers.addAll(plugin.getDatabaseManager().getActiveJailedPlayers());
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (plugin.getJailService().isJailed(p.getUniqueId())) {
                    jailedPlayers.add(p.getName());
                }
            });
            String prefix = args[0].toLowerCase();
            return jailedPlayers.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return Collections.emptyList();
    }
}
