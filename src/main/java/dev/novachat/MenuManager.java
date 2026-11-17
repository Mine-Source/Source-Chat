package dev.novachat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Menú de preferencias (/novachat menu). */
final class MenuManager {

    private static final int MENTIONS = 10, SOUNDS = 12, MESSAGES = 14, CHAT = 16, CLOSE = 22;

    private final NovaChat plugin;

    MenuManager(NovaChat plugin) {
        this.plugin = plugin;
    }

    void open(Player p) {
        NovaHolder holder = new NovaHolder(NovaHolder.Kind.MENU);
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.settings().fmt("menu-title"));
        holder.inventory(inv);
        render(p, inv);
        p.openInventory(inv);
    }

    void click(Player p, int slot, Inventory inv) {
        Profile pr = plugin.profiles().get(p.getUniqueId());
        switch (slot) {
            case MENTIONS -> pr.mentions = !pr.mentions;
            case SOUNDS -> pr.sounds = !pr.sounds;
            case MESSAGES -> pr.messages = !pr.messages;
            case CHAT -> pr.chat = !pr.chat;
            case CLOSE -> {
                p.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        plugin.profiles().touch();
        render(p, inv);
        p.playSound(p.getLocation(), "ui.button.click", 0.6f, 1.4f);
    }

    private void render(Player p, Inventory inv) {
        Profile pr = plugin.profiles().get(p.getUniqueId());
        ItemStack edge = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack back = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, (i < 9 || i >= 18) ? edge : back);

        inv.setItem(MENTIONS, toggle(Material.BELL, "mentions", pr.mentions));
        inv.setItem(SOUNDS, toggle(Material.NOTE_BLOCK, "sounds", pr.sounds));
        inv.setItem(MESSAGES, toggle(Material.WRITABLE_BOOK, "messages", pr.messages));
        inv.setItem(CHAT, toggle(Material.SPYGLASS, "chat", pr.chat));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta m = close.getItemMeta();
        m.displayName(plain(plugin.settings().fmt("menu-close")));
        close.setItemMeta(m);
        inv.setItem(CLOSE, close);
    }

    private ItemStack toggle(Material mat, String key, boolean on) {
        Settings s = plugin.settings();
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(plain(s.fmt("menu-" + key + "-name")));
        m.lore(List.of(
                plain(s.fmt("menu-" + key + "-desc")),
                Component.empty(),
                plain(s.fmt(on ? "menu-on" : "menu-off"))));
        m.setEnchantmentGlintOverride(on);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    private static Component plain(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
