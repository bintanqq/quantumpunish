package me.bintanq.quantum.commands;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.gui.HistoryGUI;
import me.bintanq.quantum.models.Punishment;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryCommand extends BaseCommand {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public HistoryCommand(QuantumPunish plugin) {
        super(plugin);
    }

    @Override
    protected String getCommandName() {
        return "history";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("quantumpunish.history")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.history"));
            return true;
        }

        boolean isGui = args[0].equalsIgnoreCase("gui");
        String targetName;
        int page = 1;

        if (isGui) {
            targetName = (args.length > 1 ? args[1] : null);
        } else {
            targetName = args[0];
            if (args.length >= 2) {
                try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
        }

        if (targetName == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage.history"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (isGui) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("player-only"));
                return true;
            }
            HistoryGUI gui = new HistoryGUI(plugin, target);
            gui.open(player, 0);
            return true;
        }

        List<Punishment> history = plugin.getPunishmentService().getHistory(target.getUniqueId());

        if (history.isEmpty()) {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("history-empty")
                    .replace("%player%", targetName)));
            return true;
        }

        int itemsPerPage = 5;
        int totalPages = (int) Math.ceil((double) history.size() / itemsPerPage);
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        sender.sendMessage(colorize(plugin.getMessageManager().getMessage("history-header")
                .replace("%player%", targetName)
                .replace("%page%", String.valueOf(page))
                .replace("%total_pages%", String.valueOf(totalPages))));

        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, history.size());
        String format = plugin.getMessageManager().getMessage("history-format");
        List<String> hoverLines = plugin.getMessageManager().getMessagesList("history-item-hover");
            for (int i = start; i < end; i++) {
            Punishment p = history.get(i);
            String dateStr = dateFormat.format(new Date(p.getTimestamp()));

            String msgLine = format
                    .replace("%date%", dateStr)
                    .replace("%type%", p.getType().name())
                    .replace("%reason%", p.getReason())
                    .replace("%staff%", p.getStaff());

            TextComponent message = new TextComponent(TextComponent.fromLegacyText(colorize(msgLine)));

            ComponentBuilder hoverBuilder = new ComponentBuilder("");
            for (int j = 0; j < hoverLines.size(); j++) {
                String line = colorize(hoverLines.get(j)
                        .replace("%type%", p.getType().name())
                        .replace("%staff%", p.getStaff())
                        .replace("%date%", dateStr)
                        .replace("%reason%", p.getReason())
                        .replace("%duration%", plugin.getPunishmentService().getInitialDuration(p)));

                hoverBuilder.append(TextComponent.fromLegacyText(line));
                if (j < hoverLines.size() - 1) hoverBuilder.append("\n");
            }

            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverBuilder.create()));
            sender.spigot().sendMessage(message);
        }

        if (totalPages > 1) {
            TextComponent footer = new TextComponent(colorize("\n§8« "));

            if (page > 1) {
                String prevText = plugin.getMessageManager().getMessage("history-nav-prev");
                String prevHover = plugin.getMessageManager().getMessage("history-nav-prev-hover")
                        .replace("%page%", String.valueOf(page - 1));

                TextComponent prev = new TextComponent(TextComponent.fromLegacyText(colorize(prevText)));
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/history " + targetName + " " + (page - 1)));
                prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(colorize(prevHover)).create()));
                footer.addExtra(prev);
            } else {
                footer.addExtra(colorize("&8" + plugin.getMessageManager().getMessage("history-nav-prev")));            }

            String pageInfo = plugin.getMessageManager().getMessage("history-nav-pages")
                    .replace("%page%", String.valueOf(page))
                    .replace("%total_pages%", String.valueOf(totalPages));

            footer.addExtra(colorize(pageInfo));

            if (page < totalPages) {
                String nextText = plugin.getMessageManager().getMessage("history-nav-next");
                String nextHover = plugin.getMessageManager().getMessage("history-nav-next-hover")
                        .replace("%page%", String.valueOf(page + 1));

                TextComponent next = new TextComponent(TextComponent.fromLegacyText(colorize(nextText)));
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/history " + targetName + " " + (page + 1)));
                next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(colorize(nextHover)).create()));
                footer.addExtra(next);
            } else {
                footer.addExtra(colorize("&8" + plugin.getMessageManager().getMessage("history-nav-next")));            }

            footer.addExtra(colorize(" §8»"));
            sender.spigot().sendMessage(footer);
        } else {
            sender.sendMessage(colorize(plugin.getMessageManager().getMessage("history-footer")
                    .replace("%player%", targetName)));
        }

        return true;
    }

    private String colorize(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("gui");
            String current = args[0].toLowerCase();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(current)) list.add(p.getName());
            });
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            List<String> players = new ArrayList<>();
            String current = args[1].toLowerCase();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(current)) players.add(p.getName());
            });
            return players;
        }
        return Collections.emptyList();
    }
}