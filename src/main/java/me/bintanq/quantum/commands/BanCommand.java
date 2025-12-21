package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class BanCommand extends BaseCommand {
    public BanCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "ban";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.ban")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (plugin.getConfigManager().isIdSystemEnabled()) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessageManager().getMessage("usage.ban-id"));
                return true;
            }

            String playerName = args[0];
            String id = args[1];
            String reason = plugin.getConfigManager().getIdForBan(id);

            if (reason == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("invalid-id"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            plugin.getPunishmentService().banPlayer(
                    playerName,
                    target.getUniqueId(),
                    sender.getName(),
                    reason,
                    null,
                    null
            );

            sender.sendMessage(plugin.getMessageManager().getMessage("ban-success")
                    .replace("%player%", playerName)
                    .replace("%reason%", reason));
        } else {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessageManager().getMessage("usage.ban"));
                return true;
            }

            String playerName = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            plugin.getPunishmentService().banPlayer(
                    playerName,
                    target.getUniqueId(),
                    sender.getName(),
                    reason,
                    null,
                    null
            );

            sender.sendMessage(plugin.getMessageManager().getMessage("ban-success")
                    .replace("%player%", playerName)
                    .replace("%reason%", reason));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            return players;
        }

        if (args.length == 2 && plugin.getConfigManager().isIdSystemEnabled()) {
            var section = plugin.getConfig().getConfigurationSection("id-system.bans");
            if (section != null) {
                return new ArrayList<>(section.getKeys(false));
            }
        }
        return Collections.emptyList();
    }
}