package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;

public class JailCommand extends BaseCommand {
    public JailCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "jail";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.jail")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        // Check if jail system is enabled
        if (!plugin.getConfig().getBoolean("jail-system.enabled", false)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-system-disabled"));
            return true;
        }

        // Usage: /jail <player> <duration> <cell> <labor> <reason...>
        if (args.length < 5) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.jail"));
            return true;
        }

        String playerName = args[0];
        String durationStr = args[1];
        String cellName = args[2];
        String laborStr = args[3];
        String reason = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        // Parse duration
        long duration = plugin.getPunishmentService().parseDuration(durationStr);
        if (duration <= 0) {
            sender.sendMessage(plugin.getMessageManager().getMessage("invalid-duration"));
            return true;
        }

        // Parse labor requirement
        int laborRequired = 0;
        if (plugin.getConfig().getBoolean("jail-system.labor.enabled", false)) {
            try {
                laborRequired = Integer.parseInt(laborStr);
                if (laborRequired < 0) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("invalid-labor"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().getMessage("invalid-labor"));
                return true;
            }
        }

        // Check if cell exists
        if (plugin.getJailService().getCellLocation(cellName) == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-cell-not-found")
                    .replace("%cell%", cellName));
            return true;
        }

        // Get player
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        // Jail the player
        boolean success = plugin.getJailService().jailPlayer(
                playerName,
                target.getUniqueId(),
                cellName,
                sender.getName(),
                reason,
                duration,
                laborRequired
        );

        if (success) {
            String message = plugin.getMessageManager().getMessage("jail-success")
                    .replace("%player%", playerName)
                    .replace("%cell%", cellName)
                    .replace("%duration%", plugin.getPunishmentService().formatDuration(duration))
                    .replace("%labor%", String.valueOf(laborRequired))
                    .replace("%reason%", reason);

            sender.sendMessage(message);

            // Broadcast if enabled
            if (plugin.getConfig().getBoolean("jail-system.broadcast", true)) {
                String broadcast = plugin.getMessageManager().getMessage("jail-broadcast")
                        .replace("%player%", playerName)
                        .replace("%staff%", sender.getName())
                        .replace("%cell%", cellName)
                        .replace("%reason%", reason);

                plugin.getServer().broadcastMessage(broadcast);
            }
        } else {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-failed"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            // Player names
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> players.add(p.getName()));
            return players;
        }

        if (args.length == 2) {
            // Duration suggestions
            return Arrays.asList("10m", "30m", "1h", "6h", "12h", "1d", "7d");
        }

        if (args.length == 3) {
            // Cell names
            return new ArrayList<>(plugin.getJailService().getCellNames());
        }

        if (args.length == 4) {
            // Labor suggestions
            if (plugin.getConfig().getBoolean("jail-system.labor.enabled", false)) {
                return Arrays.asList("0", "10", "50", "100", "500");
            } else {
                return Collections.singletonList("0");
            }
        }

        if (args.length == 5) {
            return Collections.singletonList("<reason>");
        }

        return Collections.emptyList();
    }
}
