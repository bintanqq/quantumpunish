package me.bintanq.quantum.gui;

import me.bintanq.quantum.models.Appeal;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.List;

public class AppealsHolder implements InventoryHolder {
    private final Inventory inventory;
    private final List<Appeal> appeals;
    private final int currentPage;

    public AppealsHolder(Inventory inventory, List<Appeal> appeals, int currentPage) {
        this.inventory = inventory;
        this.appeals = appeals;
        this.currentPage = currentPage;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public List<Appeal> getAppeals() { return appeals; }
    public int getCurrentPage() { return currentPage; }
}