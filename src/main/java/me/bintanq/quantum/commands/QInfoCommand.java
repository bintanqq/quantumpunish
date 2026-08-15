package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.*;

public class QInfoCommand extends BaseCommand {
    public QInfoCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "playerinfo";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.info")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.qinfo"));
            return true;
        }

        String playerName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;
        PlayerData data = targetUuid != null ? plugin.getPlayerDataService().getPlayerData(targetUuid) : null;
        if (data == null) {
            data = plugin.getPlayerDataService().getPlayerDataByName(playerName);
        }

        if (data == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-not-found"));
            return true;
        }

        UUID realUuid = data.getUuid();
        int warnings = plugin.getWarningService().getWarningPoints(realUuid);
        List<String> alts = plugin.getDatabaseManager().getAltsByIP(data.getIpAddresses());
        alts.remove(data.getLastName());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String altsDisplay = alts.isEmpty() ? "None" : String.join(", ", alts);

        // Fetch active punishments
        var activePunishments = plugin.getDatabaseManager().getActivePunishmentsForPlayer(realUuid.toString(), data.getLastName());
        List<String> activeStatusList = new ArrayList<>();
        for (var p : activePunishments) {
            String dur = plugin.getPunishmentService().formatDuration(p.getExpires() == null ? 0 : p.getExpires() - System.currentTimeMillis());
            activeStatusList.add(p.getType() + " (" + dur + " - " + p.getReason() + ")");
        }
        if (plugin.getJailService() != null && plugin.getJailService().isJailed(realUuid)) {
            var jail = plugin.getJailService().getJail(realUuid);
            if (jail != null) {
                activeStatusList.add("JAIL (Cell: " + jail.getCellName() + " | Labor: " + jail.getLaborProgress() + "/" + jail.getLaborRequired() + ")");
            }
        }
        String activeDisplay = activeStatusList.isEmpty() ? "§aNone" : "§c" + String.join(", ", activeStatusList);

        String divider = plugin.getMessageManager().getMessage("divider");
        sender.sendMessage(divider);
        sender.sendMessage(plugin.getMessageManager().getMessage("info-header")
                .replace("%player%", data.getLastName()));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-uuid")
                .replace("%uuid%", realUuid.toString()));
        sender.sendMessage("§c§lQuantum§f§lPunish §8» §fActive Punishments: " + activeDisplay);
        sender.sendMessage(plugin.getMessageManager().getMessage("info-warnings")
                .replace("%warnings%", String.valueOf(warnings)));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-first-join")
                .replace("%date%", sdf.format(new Date(data.getFirstJoin()))));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-last-seen")
                .replace("%date%", sdf.format(new Date(data.getLastSeen()))));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-ips")
                .replace("%ips%", data.getIpAddresses()));
        sender.sendMessage(plugin.getMessageManager().getMessage("info-alts")
                .replace("%alts%", altsDisplay));
        sender.sendMessage(divider);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            Set<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            suggestions.addAll(plugin.getDatabaseManager().getActivePunishedPlayers("MUTE"));
            suggestions.addAll(plugin.getDatabaseManager().getActivePunishedPlayers("BAN"));
            return new ArrayList<>(suggestions);
        }
        return Collections.emptyList();
    }
}