package me.bintanq.quantum.gui;

import me.bintanq.quantum.models.Punishment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.List;

public class ActivePunishmentHolder implements InventoryHolder {
    private final Inventory inventory;
    private final List<Punishment> activePunishments;
    private final int currentPage;

    public ActivePunishmentHolder(Inventory inventory, List<Punishment> activePunishments, int currentPage) {
        this.inventory = inventory;
        this.activePunishments = activePunishments;
        this.currentPage = currentPage;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public List<Punishment> getActivePunishments() { return activePunishments; }
    public int getCurrentPage() { return currentPage; }
}