package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class WarnCommand extends BaseCommand {
    public WarnCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "warn";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.warn")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.warn"));
            return true;
        }

        String playerName = args[0];
        String action = args[1].toLowerCase();
        int points;

        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessage("points-warn-failed"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (action.equals("add")) {
            String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason provided";

            int totalPoints = plugin.getWarningService().addWarning(target.getUniqueId(), points);

            plugin.getPunishmentService().warnPlayer(
                    playerName,
                    target.getUniqueId(),
                    sender.getName(),
                    reason,
                    points
            );

            sender.sendMessage(plugin.getMessageManager().getMessage("warn-success")
                    .replace("%player%", playerName)
                    .replace("%points%", String.valueOf(points))
                    .replace("%total%", String.valueOf(totalPoints))
                    .replace("%points_added%", String.valueOf(points))
                    .replace("%total_points%", String.valueOf(totalPoints))
                    .replace("%reason%", reason));

        } else if (action.equals("remove")) {
            int totalAfter = plugin.getWarningService().removeWarning(target.getUniqueId(), points);

            sender.sendMessage(plugin.getMessageManager().getMessage("unwarn-success")
                    .replace("%player%", playerName)
                    .replace("%points%", String.valueOf(points))
                    .replace("%points_removed%", String.valueOf(points))
                    .replace("%total_points%", String.valueOf(totalAfter))
                    .replace("%total%", String.valueOf(totalAfter)));
        } else {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.warn"));
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
        if (args.length == 2) {
            return Arrays.asList("add", "remove");
        }
        if (args.length == 3) {
            return Arrays.asList("1", "2", "3", "5", "10");
        }
        if (args.length == 4) {
            return List.of("<reason>");
        }

        return Collections.emptyList();
    }
}