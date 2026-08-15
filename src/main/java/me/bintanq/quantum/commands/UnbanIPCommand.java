package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class UnbanIPCommand extends BaseCommand {
    public UnbanIPCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "unbanip";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.unbanip")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.unbanip"));
            return true;
        }

        String ip = args[0];
        plugin.getPunishmentService().unbanIP(ip, sender.getName());
        sender.sendMessage(plugin.getMessageManager().getMessage("unbanip-success")
                .replace("%ip%", ip));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            java.util.List<String> ips = new java.util.ArrayList<>();
            org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.IP).getBanEntries().forEach(e -> ips.add(e.getTarget()));
            String prefix = args[0].toLowerCase();
            return ips.stream()
                    .filter(ip -> ip.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}