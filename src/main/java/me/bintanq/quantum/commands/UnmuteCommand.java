package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class UnmuteCommand extends BaseCommand {
    public UnmuteCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "unmute";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.unmute")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.unmute"));
            return true;
        }

        String playerName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        plugin.getPunishmentService().unmutePlayer(playerName, target.getUniqueId(), sender.getName());
        sender.sendMessage(plugin.getMessageManager().getMessage("unmute-success")
                .replace("%player%", playerName));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> mutedPlayers = plugin.getDatabaseManager().getActivePunishedPlayers("MUTE");
            String prefix = args[0].toLowerCase();
            return mutedPlayers.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}