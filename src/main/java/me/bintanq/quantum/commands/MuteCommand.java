package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class MuteCommand extends BaseCommand {
    public MuteCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "mute";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.mute")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (plugin.getConfigManager().isIdSystemEnabled()) {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessageManager().getMessage("usage.mute-id"));
                return true;
            }

            String playerName = args[0];
            String id = args[1];
            String reason = plugin.getConfigManager().getIdForMute(id);

            if (reason == null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("invalid-id"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            plugin.getPunishmentService().mutePlayer(
                    playerName,
                    target.getUniqueId(),
                    sender.getName(),
                    reason,
                    null
            );

            sender.sendMessage(plugin.getMessageManager().getMessage("mute-success")
                    .replace("%player%", playerName)
                    .replace("%reason%", reason));
        } else {
            if (args.length < 2) {
                sender.sendMessage(plugin.getMessageManager().getMessage("usage.mute"));
                return true;
            }

            String playerName = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            plugin.getPunishmentService().mutePlayer(
                    playerName,
                    target.getUniqueId(),
                    sender.getName(),
                    reason,
                    null
            );

            sender.sendMessage(plugin.getMessageManager().getMessage("mute-success")
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
        return null;
    }
}