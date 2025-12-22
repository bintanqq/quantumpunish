package me.bintanq.quantum.gui;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Appeal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BrushableBlock;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class AppealsGUI {
    private final QuantumPunish plugin;
    private final FileConfiguration config;
    private List<Appeal> pendingAppeals;
    public static final int ITEMS_PER_PAGE = 45;

    public AppealsGUI(QuantumPunish plugin) {
        this.plugin = plugin;
        this.config = plugin.getAppealsMenuConfig();
    }

    public void open(Player player, int page) {
        this.pendingAppeals = plugin.getAppealService().getPendingAppeals();

        String title = colorize(config.getString("title", "&eAppeals | Page %page%"))
                .replace("%page%", String.valueOf(page + 1));

        AppealsHolder holder = new AppealsHolder(null, pendingAppeals, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        if (!pendingAppeals.isEmpty()) {
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, pendingAppeals.size());

            for (int i = startIndex; i < endIndex; i++) {
                inv.setItem(i - startIndex, createAppealItem(pendingAppeals.get(i)));
            }

            if (page > 0) inv.setItem(48, createNavItem("previous-page"));
            if (endIndex < pendingAppeals.size()) inv.setItem(50, createNavItem("next-page"));

        } else {
            ItemStack emptySign = new ItemStack(Material.BARRIER);
            ItemMeta meta = emptySign.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(colorize("&cNo Pending Appeals"));
                emptySign.setItemMeta(meta);
            }
            inv.setItem(22, emptySign);
        }

        inv.setItem(49, createStatsItem());

        player.openInventory(inv);
    }

    private ItemStack createAppealItem(Appeal appeal) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(appeal.getUuid()));
        meta.setDisplayName(colorize(config.getString("appeal-item.name"))
                .replace("%player%", appeal.getPlayerName()));

        List<String> lore = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (String line : config.getStringList("appeal-item.lore")) {
            lore.add(colorize(line)
                    .replace("%reason%", appeal.getReason())
                    .replace("%date%", sdf.format(new Date(appeal.getTimestamp()))));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsItem() {
        Material mat = Material.valueOf(config.getString("stats-item.material", "BOOK"));
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(colorize(config.getString("stats-item.name")));

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("stats-item.lore")) {
            lore.add(colorize(line).replace("%total%", String.valueOf(pendingAppeals.size())));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(String key) {
        ItemStack item = new ItemStack(Material.valueOf(config.getString(key + ".material", "ARROW")));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(colorize(config.getString(key + ".name")));
        item.setItemMeta(meta);
        return item;
    }

    private String colorize(String s) {
        return plugin.getMessageManager().colorize(s);
    }
}