package dev.novachat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Protege los inventarios de NovaChat y gestiona entrada/salida. */
final class MenuListener implements Listener {

    private final NovaChat plugin;

    MenuListener(NovaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof NovaHolder h)) return;
        e.setCancelled(true);
        if (h.kind == NovaHolder.Kind.MENU
                && e.getWhoClicked() instanceof Player p
                && e.getClickedInventory() == e.getView().getTopInventory()) {
            plugin.menus().click(p, e.getSlot(), e.getView().getTopInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof NovaHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Settings s = plugin.settings();
        if (!s.joinQuit()) return;
        Player p = e.getPlayer();
        e.joinMessage(s.fmt("join",
                Placeholder.unparsed("player", p.getName()),
                Placeholder.component("displayname", p.displayName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Settings s = plugin.settings();
        if (!s.joinQuit()) return;
        Player p = e.getPlayer();
        e.quitMessage(s.fmt("quit",
                Placeholder.unparsed("player", p.getName()),
                Placeholder.component("displayname", p.displayName())));
    }
}
