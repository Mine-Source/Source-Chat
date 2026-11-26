package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menú genérico: un renderer rellena los ítems y asigna acciones; render() lo repinta sin reabrir. */
final class Gui {

    final Player viewer;
    private final Inventory inv;
    private final Consumer<Gui> renderer;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private ItemStack background;

    Gui(Player viewer, Component title, int rows, Consumer<Gui> renderer) {
        this.viewer = viewer;
        this.renderer = renderer;
        SourceHolder holder = new SourceHolder(SourceHolder.Kind.MENU, this);
        this.inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory(inv);
    }

    int size() {
        return inv.getSize();
    }

    Gui background(Material m) {
        this.background = Items.filler(m);
        return this;
    }

    Gui set(int slot, ItemStack it, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inv.getSize()) return this;
        inv.setItem(slot, it);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    void render() {
        inv.clear();
        actions.clear();
        renderer.accept(this);
        if (background != null) {
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) inv.setItem(i, background);
            }
        }
    }

    void open() {
        render();
        viewer.openInventory(inv);
    }

    void click(int slot, ClickType type) {
        Consumer<ClickType> a = actions.get(slot);
        if (a != null) a.accept(type);
    }
}
