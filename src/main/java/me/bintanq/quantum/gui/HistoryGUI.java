package me.bintanq.quantum.gui;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryGUI {
    private final QuantumPunish plugin;
    private final OfflinePlayer target;
    private final FileConfiguration config;
    private final List<Punishment> history;
    public static final int ITEMS_PER_PAGE = 45;

    public HistoryGUI(QuantumPunish plugin, OfflinePlayer target) {
        this.plugin = plugin;
        this.target = target;
        this.config = plugin.getHistoryMenuConfig();
        this.history = plugin.getPunishmentService().getHistory(target.getUniqueId());
    }

    public void open(Player player, int page) {
        String titleTemplate = config.getString("title", "History: %player% | Page %page%");
        String title = colorize(titleTemplate)
                .replace("%player%", target.getName())
                .replace("%page%", String.valueOf(page));

        Inventory inv = Bukkit.createInventory(
                new HistoryHolder(null, target, history, page), // << Pasang Holder di sini
                54,
                title
        );

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, history.size());

        for (int i = startIndex; i < endIndex; i++) {
            Punishment punishment = history.get(i);
            inv.setItem(i - startIndex, createPunishmentItem(punishment));
        }

        if (page > 0) {
            inv.setItem(48, createNavigationItem("previous-page"));
        }

        if (endIndex < history.size()) {
            inv.setItem(50, createNavigationItem("next-page"));
        }

        inv.setItem(49, createInfoItem());
        player.openInventory(inv);
    }

    private ItemStack createPunishmentItem(Punishment punishment) {
        Material material = getMaterialForType(punishment.getType().name());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = config.getString("punishment-item.name", "&c%type%")
                .replace("%type%", punishment.getType().name());
        meta.setDisplayName(colorize(displayName));

        List<String> lore = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (String line : config.getStringList("punishment-item.lore")) {
            lore.add(colorize(line)
                    .replace("%player%", punishment.getPlayerName())
                    .replace("%staff%", punishment.getStaff())
                    .replace("%reason%", punishment.getReason())
                    .replace("%date%", sdf.format(new Date(punishment.getTimestamp())))
                    .replace("%expires%", punishment.getExpires() == null ? "Never" :
                            sdf.format(new Date(punishment.getExpires()))));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationItem(String key) {
        String materialName = config.getString(key + ".material", "ARROW");
        Material material = Material.valueOf(materialName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(colorize(config.getString(key + ".name", "&aNext")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(colorize(config.getString("info-item.name", "&e%player% Info"))
                .replace("%player%", target.getName()));

        List<String> lore = new ArrayList<>();
        int warnings = plugin.getWarningService().getWarningPoints(target.getUniqueId());

        for (String line : config.getStringList("info-item.lore")) {
            lore.add(colorize(line)
                    .replace("%player%", target.getName())
                    .replace("%uuid%", target.getUniqueId().toString())
                    .replace("%warnings%", String.valueOf(warnings))
                    .replace("%total%", String.valueOf(history.size())));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material getMaterialForType(String type) {
        String materialName = config.getString("punishment-types." + type.toLowerCase(), "PAPER");
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    private String colorize(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}