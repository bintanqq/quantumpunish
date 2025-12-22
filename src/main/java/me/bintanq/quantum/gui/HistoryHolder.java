package me.bintanq.quantum.gui;

import me.bintanq.quantum.models.Punishment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.OfflinePlayer;

import java.util.List;

public class HistoryHolder implements InventoryHolder {

    private final Inventory inventory;
    private final OfflinePlayer target;
    private final List<Punishment> history;
    private int currentPage;

    public HistoryHolder(Inventory inventory, OfflinePlayer target, List<Punishment> history, int currentPage) {
        this.inventory = inventory;
        this.target = target;
        this.history = history;
        this.currentPage = currentPage;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public OfflinePlayer getTarget() {
        return target;
    }

    public List<Punishment> getHistory() {
        return history;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}