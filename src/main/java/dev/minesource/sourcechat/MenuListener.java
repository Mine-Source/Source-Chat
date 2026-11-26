package dev.minesource.sourcechat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Protege los inventarios de SourceChat y enruta los clicks a su menú. */
final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof SourceHolder h)) return;
        e.setCancelled(true);
        if (h.gui != null && e.getWhoClicked() instanceof Player
                && e.getClickedInventory() == e.getView().getTopInventory()) {
            h.gui.click(e.getSlot(), e.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof SourceHolder) e.setCancelled(true);
    }
}
