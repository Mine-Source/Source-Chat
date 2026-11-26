package dev.minesource.sourcechat;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca los inventarios de SourceChat (menús y vistas) para protegerlos de clicks. */
final class SourceHolder implements InventoryHolder {

    enum Kind { MENU, VIEW }

    final Kind kind;
    final Gui gui;
    private Inventory inventory;

    SourceHolder(Kind kind) {
        this(kind, null);
    }

    SourceHolder(Kind kind, Gui gui) {
        this.kind = kind;
        this.gui = gui;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
