package me.bintanq.quantum.gui;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.models.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class ActivePunishmentGUI {
    private final QuantumPunish plugin;
    private final FileConfiguration menuConfig;
    private final List<Punishment> activePunishments;
    public static final int ITEMS_PER_PAGE = 45;

    public ActivePunishmentGUI(QuantumPunish plugin) {
        this.plugin = plugin;
        this.menuConfig = plugin.getActiveMenuConfig();
        this.activePunishments = plugin.getDatabaseManager().getAllActivePunishments();
    }

    public void open(Player player, int page) {
        if (activePunishments.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage("active-gui-no-data"));
            return;
        }

        String titleTemplate = menuConfig.getString("title", "&cActive | Page %page%");
        String title = plugin.getMessageManager().colorize(titleTemplate.replace("%page%", String.valueOf(page + 1)));

        ActivePunishmentHolder holder = new ActivePunishmentHolder(null, activePunishments, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, activePunishments.size());

        for (int i = startIndex; i < endIndex; i++) {
            inv.setItem(i - startIndex, createActiveItem(activePunishments.get(i)));
        }

        if (page > 0) inv.setItem(48, createNavItem("previous-page"));
        if (endIndex < activePunishments.size()) inv.setItem(50, createNavItem("next-page"));

        inv.setItem(49, createStatsItem());

        player.openInventory(inv);
    }

    private ItemStack createActiveItem(Punishment p) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(p.getUuid()));
        meta.setDisplayName(plugin.getMessageManager().colorize(menuConfig.getString("active-item.name"))
                .replace("%player%", p.getPlayerName())
                .replace("%type%", p.getType().name()));

        List<String> lore = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String remaining = formatRemaining(p.getExpires());

        for (String line : menuConfig.getStringList("active-item.lore")) {
            lore.add(plugin.getMessageManager().colorize(line)
                    .replace("%staff%", p.getStaff())
                    .replace("%reason%", p.getReason())
                    .replace("%date%", sdf.format(new Date(p.getTimestamp())))
                    .replace("%remaining%", remaining));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsItem() {
        Material mat = Material.valueOf(menuConfig.getString("stats-item.material", "BEACON"));
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().colorize(menuConfig.getString("stats-item.name")));

        List<String> lore = new ArrayList<>();
        String timeNow = new SimpleDateFormat("HH:mm:ss").format(new Date());
        for (String line : menuConfig.getStringList("stats-item.lore")) {
            lore.add(plugin.getMessageManager().colorize(line)
                    .replace("%total%", String.valueOf(activePunishments.size()))
                    .replace("%time%", timeNow));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(String key) {
        ItemStack item = new ItemStack(Material.valueOf(menuConfig.getString(key + ".material", "ARROW")));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getMessageManager().colorize(menuConfig.getString(key + ".name")));
        item.setItemMeta(meta);
        return item;
    }

    private String formatRemaining(Long expires) {
        if (expires == null || expires <= 0) return "&4Permanent";
        long diff = expires - System.currentTimeMillis();
        if (diff <= 0) return "&cExpired";
        long d = diff / 86400000;
        long h = (diff / 3600000) % 24;
        long m = (diff / 60000) % 60;
        return String.format("&e%dd %dh %dm", d, h, m);
    }
}