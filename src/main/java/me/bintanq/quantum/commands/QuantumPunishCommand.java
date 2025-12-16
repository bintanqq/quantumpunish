package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigurations();
            sender.sendMessage(plugin.getMessageManager().getMessage("reload-success"));
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("unknown-subcommand"));
        return true;
    }

    private void showHelpMenu(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("help-header"));

        sender.sendMessage(plugin.getMessageManager().getMessage("help-reload"));

        String helpCommandRaw = "&f/quantumpunish help &8- &7Show this help menu";
        sender.sendMessage(plugin.getMessageManager().colorize(helpCommandRaw));

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

                // Format teks sesuai dengan help-reload: "&f/<command> &8- &7<description>"
                String rawMessage = "&f/" + commandName + " &8- &7" + description;

                sender.sendMessage(plugin.getMessageManager().colorize(rawMessage));
            }
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "help");
        }
        return null;
    }
}