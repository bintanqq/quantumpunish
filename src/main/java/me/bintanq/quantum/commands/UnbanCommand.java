package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class UnbanCommand extends BaseCommand {
    public UnbanCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "unban";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.unban")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.unban"));
            return true;
        }

        String playerName = args[0];
        plugin.getPunishmentService().unbanPlayer(playerName, sender.getName());
        sender.sendMessage(plugin.getMessageManager().getMessage("unban-success")
                .replace("%player%", playerName));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            java.util.Set<String> banned = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            banned.addAll(plugin.getDatabaseManager().getActivePunishedPlayers("BAN"));
            org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries().forEach(e -> banned.add(e.getTarget()));
            String prefix = args[0].toLowerCase();
            return banned.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}