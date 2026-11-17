package dev.novachat;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca los inventarios de NovaChat (menú y vistas) para protegerlos de clicks. */
final class NovaHolder implements InventoryHolder {

    enum Kind { MENU, VIEW }

    final Kind kind;
    private Inventory inventory;

    NovaHolder(Kind kind) {
        this.kind = kind;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
