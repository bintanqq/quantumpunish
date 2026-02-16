package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class JailAdminCommand extends BaseCommand {
    public JailAdminCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "jailadmin";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.jail.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setcell" -> handleSetCell(sender, args);
            case "removecell" -> handleRemoveCell(sender, args);
            case "listcells" -> handleListCells(sender);
            case "setspawner" -> handleSetSpawner(sender, args);
            case "removespawner" -> handleRemoveSpawner(sender, args);
            case "listspawners" -> handleListSpawners(sender, args);
            case "setrelease" -> handleSetRelease(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    private void handleSetCell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cUsage: /jailadmin setcell <cell_name>"));
            return;
        }

        String cellName = args[1];
        Location location = player.getLocation();

        plugin.getJailService().setCellLocation(cellName, location);

        sender.sendMessage(plugin.getMessageManager().getMessage("jail-cell-set")
                .replace("%cell%", cellName)
                .replace("%x%", String.format("%.2f", location.getX()))
                .replace("%y%", String.format("%.2f", location.getY()))
                .replace("%z%", String.format("%.2f", location.getZ())));
    }

    private void handleRemoveCell(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cUsage: /jailadmin removecell <cell_name>"));
            return;
        }

        String cellName = args[1];

        if (plugin.getJailService().getCellLocation(cellName) == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-cell-not-found")
                    .replace("%cell%", cellName));
            return;
        }

        plugin.getConfig().set("jail-system.cells." + cellName, null);
        plugin.saveConfig();

        sender.sendMessage(plugin.getMessageManager().getMessage("jail-cell-removed")
                .replace("%cell%", cellName));
    }

    private void handleListCells(CommandSender sender) {
        Set<String> cells = plugin.getJailService().getCellNames();

        if (cells.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-no-cells"));
            return;
        }

        sender.sendMessage(plugin.getMessageManager().colorize("&c&lAvailable Jail Cells:"));
        for (String cell : cells) {
            Location loc = plugin.getJailService().getCellLocation(cell);
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&f- &c" + cell + " &7(" + loc.getWorld().getName() +
                            " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"));
        }
    }

    private void handleSetSpawner(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
            return;
        }

        if (!plugin.getConfig().getBoolean("jail-system.labor.enabled", false)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-labor-disabled"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cUsage: /jailadmin setspawner <cell_name> [x] [y] [z]"));
            return;
        }

        String cellName = args[1];
        Location location;

        if (args.length >= 5) {
            // Manual coordinates
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                location = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getMessageManager().getMessage("invalid-coordinates"));
                return;
            }
        } else {
            // Current location
            location = player.getLocation().getBlock().getLocation();
        }

        plugin.getLaborManager().addSpawnerLocation(cellName, location);

        sender.sendMessage(plugin.getMessageManager().getMessage("jail-spawner-set")
                .replace("%cell%", cellName)
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ())));

        // Place the block
        location.getBlock().setType(plugin.getLaborManager().getLaborBlockMaterial());
    }

    private void handleRemoveSpawner(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cUsage: /jailadmin removespawner <cell_name>"));
            return;
        }

        String cellName = args[1];
        Location location = player.getTargetBlock(null, 5).getLocation();

        plugin.getLaborManager().removeSpawnerLocation(cellName, location);

        sender.sendMessage(plugin.getMessageManager().getMessage("jail-spawner-removed")
                .replace("%cell%", cellName));
    }

    private void handleListSpawners(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cUsage: /jailadmin listspawners <cell_name>"));
            return;
        }

        String cellName = args[1];
        List<Location> spawners = plugin.getLaborManager().getSpawnerLocations(cellName);

        if (spawners.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("jail-no-spawners")
                    .replace("%cell%", cellName));
            return;
        }

        sender.sendMessage(plugin.getMessageManager().colorize(
                "&c&lSpawners for cell: &f" + cellName));

        int index = 1;
        for (Location loc : spawners) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                    "&f" + index + ". &7" + loc.getBlockX() + ", " +
                            loc.getBlockY() + ", " + loc.getBlockZ()));
            index++;
        }
    }

    private void handleSetRelease(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
            return;
        }

        Location location = player.getLocation();
        plugin.getJailService().setReleaseLocation(location);

        sender.sendMessage(plugin.getMessageManager().getMessage("jail-release-set")
                .replace("%x%", String.format("%.2f", location.getX()))
                .replace("%y%", String.format("%.2f", location.getY()))
                .replace("%z%", String.format("%.2f", location.getZ())));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().colorize("&c&m          &c&lJail Admin Help&c&m          "));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin setcell <name> &8- &7Set jail cell"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin removecell <name> &8- &7Remove cell"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin listcells &8- &7List all cells"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin setspawner <cell> [x y z] &8- &7Set labor spawner"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin removespawner <cell> &8- &7Remove spawner (looking at)"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin listspawners <cell> &8- &7List spawners"));
        sender.sendMessage(plugin.getMessageManager().colorize("&f/jailadmin setrelease &8- &7Set release spawn point"));
        sender.sendMessage(plugin.getMessageManager().colorize("&c&m                                            "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setcell", "removecell", "listcells",
                    "setspawner", "removespawner", "listspawners", "setrelease");
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("removecell") || subCmd.equals("setspawner") ||
                    subCmd.equals("removespawner") || subCmd.equals("listspawners")) {
                return new ArrayList<>(plugin.getJailService().getCellNames());
            }
            if (subCmd.equals("setcell")) {
                return Collections.singletonList("<cell_name>");
            }
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("setspawner")) {
            if (args.length == 3) return Collections.singletonList("<x>");
            if (args.length == 4) return Collections.singletonList("<y>");
            if (args.length == 5) return Collections.singletonList("<z>");
        }

        return Collections.emptyList();
    }
}
