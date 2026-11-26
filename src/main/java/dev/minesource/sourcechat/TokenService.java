package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Tokens interactivos del chat: [item], [inv], [ender], [pos]. */
final class TokenService {

    private static final Pattern TOKEN = Pattern.compile("\\[(item|inv|ender|pos)]", Pattern.CASE_INSENSITIVE);

    private record Snapshot(Component title, ItemStack[] items, boolean playerInv, long expires) {
    }

    private record Data(ItemStack hand, ItemStack[] inv, ItemStack[] ender, Location loc) {
    }

    private final SourceChat plugin;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private BukkitTask cleaner;

    TokenService(SourceChat plugin) {
        this.plugin = plugin;
    }

    void start() {
        cleaner = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            snapshots.values().removeIf(s -> s.expires() < now);
        }, 1200L, 1200L);
    }

    void stop() {
        if (cleaner != null) cleaner.cancel();
        snapshots.clear();
    }

    /** Reemplaza los tokens por componentes interactivos. Se llama desde el hilo asíncrono del chat. */
    Component process(Player p, Component msg, String plain) {
        if (!TOKEN.matcher(plain).find()) return msg;

        final Data d;
        if (Bukkit.isPrimaryThread()) {
            d = capture(p);
        } else {
            try {
                d = Bukkit.getScheduler().callSyncMethod(plugin, () -> capture(p)).get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return msg;
            } catch (ExecutionException | TimeoutException e) {
                return msg;
            }
        }

        Settings s = plugin.settings();
        return msg.replaceText(TextReplacementConfig.builder().match(TOKEN).replacement((mr, b) -> {
            String t = mr.group(1).toLowerCase(Locale.ROOT);
            if (!p.hasPermission("sourcechat.token." + t)) return b;
            return switch (t) {
                case "item" -> item(d.hand());
                case "inv" -> view(s.fmt("token-inv", Placeholder.unparsed("player", p.getName())),
                        s.fmt("view-title-inv", Placeholder.unparsed("player", p.getName())), d.inv(), true);
                case "ender" -> view(s.fmt("token-ender", Placeholder.unparsed("player", p.getName())),
                        s.fmt("view-title-ender", Placeholder.unparsed("player", p.getName())), d.ender(), false);
                default -> pos(s, d.loc());
            };
        }).build());
    }

    private static Data capture(Player p) {
        return new Data(
                p.getInventory().getItemInMainHand().clone(),
                copy(p.getInventory().getContents()),
                copy(p.getEnderChest().getContents()),
                p.getLocation());
    }

    private Component item(ItemStack stack) {
        if (stack.getType().isAir()) {
            return plugin.settings().fmt("token-empty-hand");
        }
        Component c = stack.displayName();
        if (stack.getAmount() > 1) {
            c = c.append(Component.text(" x" + stack.getAmount(), NamedTextColor.GRAY));
        }
        return c;
    }

    private Component view(Component label, Component title, ItemStack[] items, boolean playerInv) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        snapshots.put(id, new Snapshot(title, items, playerInv,
                System.currentTimeMillis() + plugin.settings().snapshotMs()));
        return label
                .hoverEvent(HoverEvent.showText(plugin.settings().fmt("token-hover")))
                .clickEvent(ClickEvent.runCommand("/sourcechat view " + id));
    }

    private Component pos(Settings s, Location l) {
        String coords = l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        return s.fmt("token-pos",
                        Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                        Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                        Placeholder.unparsed("z", String.valueOf(l.getBlockZ())),
                        Placeholder.unparsed("world", l.getWorld() == null ? "?" : l.getWorld().getName()))
                .hoverEvent(HoverEvent.showText(s.fmt("token-pos-hover")))
                .clickEvent(ClickEvent.copyToClipboard(coords));
    }

    /** Abre la vista de solo lectura de un snapshot. Hilo principal. */
    void open(Player viewer, String id) {
        Snapshot s = snapshots.get(id);
        if (s == null || s.expires() < System.currentTimeMillis()) {
            viewer.sendMessage(plugin.settings().msg("view-expired"));
            return;
        }
        SourceHolder holder = new SourceHolder(SourceHolder.Kind.VIEW);
        Inventory inv;
        if (s.playerInv()) {
            inv = Bukkit.createInventory(holder, 54, s.title());
            ItemStack[] it = s.items();
            for (int i = 0; i < 27; i++) inv.setItem(i, at(it, 9 + i));
            for (int i = 0; i < 9; i++) inv.setItem(27 + i, at(it, i));
            ItemStack filler = filler();
            for (int i = 36; i < 54; i++) inv.setItem(i, filler);
            inv.setItem(45, at(it, 39)); // casco
            inv.setItem(46, at(it, 38)); // pechera
            inv.setItem(47, at(it, 37)); // pantalones
            inv.setItem(48, at(it, 36)); // botas
            inv.setItem(49, at(it, 40)); // mano secundaria
        } else {
            inv = Bukkit.createInventory(holder, 27, s.title());
            for (int i = 0; i < 27; i++) inv.setItem(i, at(s.items(), i));
        }
        holder.inventory(inv);
        viewer.openInventory(inv);
    }

    private static ItemStack at(ItemStack[] a, int i) {
        return i < a.length ? a[i] : null;
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack[] copy(ItemStack[] a) {
        ItemStack[] out = new ItemStack[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] == null ? null : a[i].clone();
        return out;
    }
}
