package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Jail;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class JailStatusCommand extends BaseCommand {
    public JailStatusCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "jailstatus";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Check own status or others (if has permission)
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
                return true;
            }

            Player player = (Player) sender;
            showJailStatus(sender, player.getUniqueId(), player.getName());
        } else {
            if (!sender.hasPermission("quantumpunish.jailstatus.others")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                return true;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            showJailStatus(sender, target.getUniqueId(), targetName);
        }

        return true;
    }

    private void showJailStatus(CommandSender sender, java.util.UUID uuid, String playerName) {
        if (!plugin.getJailService().isJailed(uuid)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-not-jailed")
                    .replace("%player%", playerName));
            return;
        }

        Jail jail = plugin.getJailService().getJail(uuid);
        if (jail == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-not-jailed")
                    .replace("%player%", playerName));
            return;
        }

        long remaining = jail.getExpires() - System.currentTimeMillis();

        List<String> statusMessages = plugin.getMessageManager().getMessagesList("jail-status");
        for (String msg : statusMessages) {
            sender.sendMessage(msg
                    .replace("%player%", playerName)
                    .replace("%cell%", jail.getCellName())
                    .replace("%staff%", jail.getStaff())
                    .replace("%reason%", jail.getReason())
                    .replace("%time_remaining%", formatDuration(remaining))
                    .replace("%labor_progress%", String.valueOf(jail.getLaborProgress()))
                    .replace("%labor_required%", String.valueOf(jail.getLaborRequired()))
                    .replace("%labor_percentage%", String.valueOf(
                            jail.getLaborRequired() > 0
                                    ? (jail.getLaborProgress() * 100 / jail.getLaborRequired())
                                    : 100
                    )));
        }
    }

    private String formatDuration(long millis) {
        if (millis < 0) return "Expired";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("quantumpunish.jailstatus.others")) {
            return null; // Default player list
        }
        return Collections.emptyList();
    }
}
