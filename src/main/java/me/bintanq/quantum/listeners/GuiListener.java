package me.bintanq.quantum.listeners;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.gui.*;
import me.bintanq.quantum.models.Appeal;
import me.bintanq.quantum.models.Punishment;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {
    private final QuantumPunish plugin;

    public GuiListener(QuantumPunish plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof HistoryHolder) &&
                !(holder instanceof ActivePunishmentHolder) &&
                !(holder instanceof AppealsHolder)) {
            return;
        }

        event.setCancelled(true); // Safety first

        if (event.getRawSlot() > 53 || event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (holder instanceof AppealsHolder appealsHolder) {
            handleAppealsClick(event, player, appealsHolder, slot);
        }
        else if (holder instanceof ActivePunishmentHolder activeHolder) {
            handleActiveClick(player, activeHolder, slot);
        }
        else if (holder instanceof HistoryHolder historyHolder) {
            handleHistoryClick(player, historyHolder, slot);
        }
    }

    private void handleAppealsClick(InventoryClickEvent event, Player player, AppealsHolder holder, int slot) {
        if (slot == 48) {
            if (holder.getCurrentPage() > 0) new AppealsGUI(plugin).open(player, holder.getCurrentPage() - 1);
            return;
        } else if (slot == 50) {
            int maxPage = (holder.getAppeals().size() - 1) / AppealsGUI.ITEMS_PER_PAGE;
            if (holder.getCurrentPage() < maxPage) new AppealsGUI(plugin).open(player, holder.getCurrentPage() + 1);
            return;
        }

        int index = (holder.getCurrentPage() * AppealsGUI.ITEMS_PER_PAGE) + slot;
        if (index >= holder.getAppeals().size()) return;
        if (event.getCurrentItem().getType() == Material.BARRIER) return;

        Appeal appeal = holder.getAppeals().get(index);

        if (event.getClick().isLeftClick()) {
            plugin.getPunishmentService().unmutePlayer(appeal.getPlayerName(), appeal.getUuid(), player.getName());
            plugin.getAppealService().resolveAppeal(appeal.getId(), true, player.getName());
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-approved-staff").replace("%player%", appeal.getPlayerName()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
        else if (event.getClick().isRightClick()) {
            plugin.getAppealService().resolveAppeal(appeal.getId(), false, player.getName());
            player.sendMessage(plugin.getMessageManager().getMessage("appeal-rejected-staff").replace("%player%", appeal.getPlayerName()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        } else {
            return;
        }

        // --- BARIS INI WAJIB ADA ---
        // Hapus dari list holder saat ini biar instan ilang
        holder.getAppeals().remove(index);

        // Refresh dengan jeda 2 tick biar SQLite selesai nulis status 'APPROVED' atau 'REJECTED'
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                new AppealsGUI(plugin).open(player, holder.getCurrentPage());
            }
        }, 2L);
    }

    private void handleActiveClick(Player player, ActivePunishmentHolder holder, int slot) {
        if (slot == 48) {
            if (holder.getCurrentPage() > 0) new ActivePunishmentGUI(plugin).open(player, holder.getCurrentPage() - 1);
            return;
        } else if (slot == 50) {
            int maxPage = (holder.getActivePunishments().size() - 1) / ActivePunishmentGUI.ITEMS_PER_PAGE;
            if (holder.getCurrentPage() < maxPage) new ActivePunishmentGUI(plugin).open(player, holder.getCurrentPage() + 1);
            return;
        }

        int index = (holder.getCurrentPage() * ActivePunishmentGUI.ITEMS_PER_PAGE) + slot;
        if (index < holder.getActivePunishments().size()) {
            Punishment p = holder.getActivePunishments().get(index);
            String msg = plugin.getMessageManager().getMessage("active-item-hint")
                    .replace("%player%", p.getPlayerName());
            player.sendMessage(msg);
        }
    }

    private void handleHistoryClick(Player player, HistoryHolder holder, int slot) {
        if (slot == 48) {
            if (holder.getCurrentPage() > 0) new HistoryGUI(plugin, holder.getTarget()).open(player, holder.getCurrentPage() - 1);
            return;
        } else if (slot == 50) {
            int maxPage = (holder.getHistory().size() - 1) / HistoryGUI.ITEMS_PER_PAGE;
            if (holder.getCurrentPage() < maxPage) new HistoryGUI(plugin, holder.getTarget()).open(player, holder.getCurrentPage() + 1);
            return;
        }
    }
}