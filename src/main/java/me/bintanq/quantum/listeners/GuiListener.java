package me.bintanq.quantum.listeners;

import me.bintanq.quantum.QuantumPunish;
import me.bintanq.quantum.gui.HistoryGUI;
import me.bintanq.quantum.gui.HistoryHolder;
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

        if (!(holder instanceof HistoryHolder historyHolder)) {
            return;
        }

        event.setCancelled(true); // <-- PENTING!

        int slot = event.getSlot();

        if (event.getRawSlot() > 53) {
            return;
        }

        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (slot == 48) { // Previous Page
            int currentPage = historyHolder.getCurrentPage();
            if (currentPage > 0) {
                new HistoryGUI(plugin, historyHolder.getTarget()).open(player, currentPage - 1);
            }
        } else if (slot == 50) { // Next Page
            int currentPage = historyHolder.getCurrentPage();
            int maxPage = (historyHolder.getHistory().size() - 1) / HistoryGUI.ITEMS_PER_PAGE;
            if (currentPage < maxPage) {
                new HistoryGUI(plugin, historyHolder.getTarget()).open(player, currentPage + 1);
            }
        }
    }
}