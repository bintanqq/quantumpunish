package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.gui.ActivePunishmentGUI;
import me.bintanq.quantum.gui.AppealsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuantumPunishCommand extends BaseCommand {
    public QuantumPunishCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "quantumpunish";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelpMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            plugin.reloadConfigurations();
            sender.sendMessage(plugin.getMessageManager().getMessage("reload-success"));
            return true;
        }

        if (sub.equals("active")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
                return true;
            }
            new ActivePunishmentGUI(plugin).open(player, 0);
            return true;
        }

        if (sub.equals("appeals")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
                return true;
            }
            new AppealsGUI(plugin).open(player, 0);
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("unknown-subcommand"));
        return true;
    }

    private void showHelpMenu(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("help-header"));

        sender.sendMessage(plugin.getMessageManager().getMessage("help-reload"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/quantumpunish active &8- &7View all active punishments"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/quantumpunish appeals &8- &7Review player appeals"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/quantumpunish help &8- &7Show this help menu"));

        Map<String, Map<String, Object>> commands = plugin.getDescription().getCommands();

        for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
            String commandName = entry.getKey();
            Map<String, Object> commandDetails = entry.getValue();

            if (commandName.equalsIgnoreCase(getCommandName())) {
                continue;
            }

            String permission = (String) commandDetails.get("permission");
            String description = (String) commandDetails.get("description");

            if (description == null) description = "No description available";

            if (permission == null || sender.hasPermission(permission)) {

                String rawMessage = "&f/" + commandName + " &8- &7" + description;

                sender.sendMessage(plugin.getMessageManager().colorize(rawMessage));
            }
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = Arrays.asList("reload", "help", "active", "appeals");
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}