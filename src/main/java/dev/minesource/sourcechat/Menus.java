package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Todos los menús: inicio, preferencias, idioma (con banderas), tienda de cosméticos y panel de administración. */
final class Menus {

    private static final String[] MODULES = {"cosmetics", "namesync", "games", "announcements", "discord", "tokens", "mentions", "joinquit"};
    private static final int[] MODULE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};
    private static final int[] SLOW_STEPS = {0, 3, 5, 10, 30};

    private final SourceChat plugin;

    Menus(SourceChat plugin) {
        this.plugin = plugin;
    }

    private Settings st() {
        return plugin.settings();
    }

    private Component t(Player p, String key, TagResolver... r) {
        return st().fmt(p, key, r);
    }

    private ItemStack btn(Player p, Material m, String key, TagResolver... r) {
        return Items.of(m, t(p, key + "-name", r), st().lines(p, key + "-lore", r));
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), "ui.button.click", 0.6f, 1.4f);
    }

    private void back(Gui g, Player p, int slot, Runnable to) {
        g.set(slot, Items.of(Material.ARROW, t(p, "menu-back"), null), c -> {
            click(p);
            to.run();
        });
    }

    private void disabled(Player p) {
        p.sendMessage(st().msg(p, "module-disabled"));
    }

    // ================================================================ INICIO

    void openHub(Player p) {
        new Gui(p, t(p, "menu-hub-title"), 5, g -> {
            Profile pr = plugin.profiles().of(p);
            g.set(10, btn(p, Material.ENDER_CHEST, "menu-hub-shop"), c -> {
                click(p);
                if (plugin.cosmetics() == null) disabled(p);
                else openShop(p);
            });
            g.set(12, btn(p, Material.COMPARATOR, "menu-hub-prefs"), c -> {
                click(p);
                openPrefs(p);
            });
            g.set(14, flag(p, plugin.lang().of(p), true, "menu-hub-lang"), c -> {
                click(p);
                openLang(p);
            });
            g.set(16, btn(p, Material.FIREWORK_ROCKET, "menu-hub-games", Placeholder.unparsed("wins", String.valueOf(pr.wins))), c -> {
                click(p);
                if (plugin.games() == null) {
                    disabled(p);
                } else {
                    p.closeInventory();
                    plugin.games().top(p);
                }
            });
            g.set(22, btn(p, Material.GOLD_INGOT, "menu-hub-coins",
                    Placeholder.unparsed("coins", plugin.eco().fmt(plugin.eco().balance(p)))), null);
            g.set(28, btn(p, Material.NAME_TAG, "menu-hub-nick"), c -> {
                click(p);
                if (!p.hasPermission("sourcechat.nick")) {
                    p.sendMessage(st().msg(p, "no-permission"));
                } else if (c.isRightClick()) {
                    plugin.names().resetNick(p, p);
                    g.render();
                } else {
                    plugin.prompts().ask(p, "prompt-nick", text -> plugin.names().setNick(p, p, text));
                }
            });
            g.set(30, btn(p, Material.BOOK, "menu-hub-help"), c -> {
                p.closeInventory();
                p.sendMessage(st().fmt(p, "help"));
            });
            if (p.hasPermission("sourcechat.mod")) {
                g.set(32, btn(p, Material.COMMAND_BLOCK, "menu-hub-admin"), c -> {
                    click(p);
                    openAdmin(p);
                });
            }
            g.set(34, btn(p, Material.BARRIER, "menu-hub-close"), c -> p.closeInventory());
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    // ================================================================ PREFERENCIAS

    void openPrefs(Player p) {
        new Gui(p, t(p, "menu-prefs-title"), 3, g -> {
            Profile pr = plugin.profiles().of(p);
            toggle(g, p, 10, Material.BELL, "menu-prefs-mentions", pr.mentions, () -> pr.mentions = !pr.mentions);
            toggle(g, p, 12, Material.NOTE_BLOCK, "menu-prefs-sounds", pr.sounds, () -> pr.sounds = !pr.sounds);
            toggle(g, p, 14, Material.WRITABLE_BOOK, "menu-prefs-messages", pr.messages, () -> pr.messages = !pr.messages);
            toggle(g, p, 16, Material.SPYGLASS, "menu-prefs-chat", pr.chat, () -> pr.chat = !pr.chat);
            back(g, p, 22, () -> openHub(p));
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    private void toggle(Gui g, Player p, int slot, Material m, String key, boolean on, Runnable flip) {
        List<Component> lore = new ArrayList<>(st().lines(p, key + "-lore"));
        lore.add(Component.empty());
        lore.add(t(p, on ? "menu-on" : "menu-off"));
        g.set(slot, Items.glow(Items.of(m, t(p, key + "-name"), lore), on), c -> {
            flip.run();
            plugin.profiles().touch();
            click(p);
            g.render();
        });
    }

    // ================================================================ IDIOMA

    private ItemStack flag(Player p, String code, boolean current, String keyPrefix) {
        Lang.Meta m = plugin.lang().meta(code);
        Component name = Text.MM.deserialize(m.name());
        List<Component> lore = new ArrayList<>();
        if (keyPrefix.equals("menu-hub-lang")) {
            lore.addAll(st().lines(p, "menu-hub-lang-lore", Placeholder.unparsed("language", Styler.plain(m.name(), 30))));
        } else {
            lore.add(t(p, current ? "menu-lang-current" : "menu-lang-click"));
            if (p.hasPermission("sourcechat.admin")) lore.add(t(p, "menu-lang-admin"));
        }
        ItemStack it;
        if (m.flag() != null && !m.flag().isBlank()) {
            it = Items.head(m.flag(), name, lore);
        } else {
            Material icon = m.icon() == null ? null : Material.matchMaterial(m.icon());
            it = Items.of(icon != null ? icon : Material.WHITE_BANNER, name, lore);
        }
        return current ? Items.glow(it, true) : it;
    }

    void openLang(Player p) {
        new Gui(p, t(p, "menu-lang-title"), 4, g -> {
            List<String> codes = plugin.lang().codes();
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            String cur = plugin.lang().of(p);
            for (int i = 0; i < codes.size() && i < slots.length; i++) {
                String code = codes.get(i);
                g.set(slots[i], flag(p, code, code.equals(cur), "menu-lang"), c -> {
                    click(p);
                    String shown = Styler.plain(plugin.lang().meta(code).name(), 30);
                    if (c.isRightClick() && p.hasPermission("sourcechat.admin")) {
                        plugin.getConfig().set("language.default", code);
                        plugin.saveConfig();
                        plugin.lang().load();
                        p.sendMessage(st().msg(p, "language-default-set", Placeholder.unparsed("language", shown)));
                    } else {
                        plugin.profiles().of(p).lang = code;
                        plugin.profiles().touch();
                        p.sendMessage(st().msg(p, "language-set", Placeholder.unparsed("language", shown)));
                    }
                    openLang(p);
                });
            }
            back(g, p, 31, () -> openHub(p));
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    // ================================================================ TIENDA

    void openShop(Player p) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) {
            disabled(p);
            return;
        }
        new Gui(p, t(p, "menu-shop-title"), 5, g -> {
            Profile pr = plugin.profiles().of(p);
            int[] slots = {10, 12, 14, 16, 20, 22, 24};
            CType[] types = CType.values();
            for (int i = 0; i < types.length; i++) {
                CType ty = types[i];
                List<Cosmetics.Item> list = c.list(ty, true);
                long owned = list.stream().filter(it -> c.owns(p, pr, it)).count();
                Cosmetics.Item eq = c.equipped(pr, ty);
                List<Component> lore = new ArrayList<>(st().lines(p, "type-" + ty.id + "-desc"));
                lore.add(Component.empty());
                lore.add(t(p, "menu-shop-count", Placeholder.unparsed("owned", String.valueOf(owned)),
                        Placeholder.unparsed("total", String.valueOf(list.size()))));
                lore.add(eq == null ? t(p, "menu-shop-none")
                        : t(p, "menu-shop-equipped", Placeholder.component("item", c.render(eq, p.getName()))));
                lore.add(Component.empty());
                lore.add(t(p, "menu-shop-open"));
                g.set(slots[i], Items.of(ty.icon, t(p, "type-" + ty.id), lore), cl -> {
                    click(p);
                    openCategory(p, ty, 0);
                });
            }
            g.set(4, btn(p, Material.GOLD_INGOT, "menu-hub-coins",
                    Placeholder.unparsed("coins", plugin.eco().fmt(plugin.eco().balance(p)))), null);
            back(g, p, 40, () -> openHub(p));
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    private static int itemSlot(int i) {
        return (1 + i / 7) * 9 + 1 + i % 7;
    }

    void openCategory(Player p, CType type, int page) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) {
            disabled(p);
            return;
        }
        Component title = t(p, "menu-cat-title", Placeholder.unparsed("type", Styler.plain(st().raw(p, "type-" + type.id), 30)));
        new Gui(p, title, 6, g -> {
            Profile pr = plugin.profiles().of(p);
            List<Cosmetics.Item> list = c.list(type, true);
            int pages = Math.max(1, (list.size() + 27) / 28);
            int pg = Math.min(Math.max(0, page), pages - 1);
            for (int i = 0; i < 28; i++) {
                int idx = pg * 28 + i;
                if (idx >= list.size()) break;
                Cosmetics.Item it = list.get(idx);
                g.set(itemSlot(i), cosmeticIcon(p, pr, it), cl -> clickCosmetic(g, p, type, pg, it, cl));
            }
            back(g, p, 45, () -> openShop(p));
            if (pg > 0) {
                g.set(48, Items.of(Material.ARROW, t(p, "menu-prev"), null), cl -> openCategory(p, type, pg - 1));
            }
            g.set(49, Items.of(Material.PAPER, t(p, "menu-page", Placeholder.unparsed("page", String.valueOf(pg + 1)),
                    Placeholder.unparsed("pages", String.valueOf(pages))),
                    List.of(t(p, "menu-hub-coins-line", Placeholder.unparsed("coins", plugin.eco().fmt(plugin.eco().balance(p)))))), null);
            if (pg < pages - 1) {
                g.set(50, Items.of(Material.ARROW, t(p, "menu-next"), null), cl -> openCategory(p, type, pg + 1));
            }
            g.set(53, btn(p, Material.BARRIER, "menu-cat-unequip"), cl -> {
                c.unequip(p, type);
                p.sendMessage(st().msg(p, "cos-unequipped"));
                click(p);
                g.render();
            });
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    private ItemStack cosmeticIcon(Player p, Profile pr, Cosmetics.Item it) {
        Cosmetics c = plugin.cosmetics();
        Cosmetics.Item cur = c.equipped(pr, it.type());
        boolean eq = cur != null && cur.id().equals(it.id());
        boolean owns = c.owns(p, pr, it);
        List<Component> lore = new ArrayList<>();
        lore.add(t(p, "rarity-" + it.rarity()));
        lore.add(Component.empty());
        if (eq) lore.add(t(p, "menu-cos-equipped"));
        else if (owns) lore.add(t(p, "menu-cos-owned"));
        else lore.add(t(p, "menu-cos-price", Placeholder.unparsed("price", plugin.eco().fmt(it.price()))));
        lore.add(Component.empty());
        lore.addAll(st().lines(p, eq ? "menu-cos-hint-unequip" : owns ? "menu-cos-hint-equip" : "menu-cos-hint-buy"));
        return Items.glow(Items.of(it.icon(), c.render(it, p.getName()), lore), eq);
    }

    private void clickCosmetic(Gui g, Player p, CType type, int pg, Cosmetics.Item it, ClickType click) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) return;
        Profile pr = plugin.profiles().of(p);
        if (click.isRightClick()) {
            c.preview(p, it);
            return;
        }
        click(p);
        Cosmetics.Item eq = c.equipped(pr, type);
        if (eq != null && eq.id().equals(it.id())) {
            c.unequip(p, type);
            p.sendMessage(st().msg(p, "cos-unequipped"));
            g.render();
        } else if (c.owns(p, pr, it)) {
            c.equip(p, it);
            p.sendMessage(st().msg(p, "cos-equipped", Placeholder.component("item", c.render(it, p.getName()))));
            g.render();
        } else {
            openConfirm(p, type, pg, it);
        }
    }

    private void openConfirm(Player p, CType type, int pg, Cosmetics.Item it) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) return;
        new Gui(p, t(p, "menu-confirm-title"), 3, g -> {
            Profile pr = plugin.profiles().of(p);
            g.set(11, btn(p, Material.LIME_CONCRETE, "menu-confirm-yes",
                    Placeholder.unparsed("price", plugin.eco().fmt(it.price()))), cl -> {
                buy(p, it);
                openCategory(p, type, pg);
            });
            g.set(13, cosmeticIcon(p, pr, it), cl -> c.preview(p, it));
            g.set(15, btn(p, Material.RED_CONCRETE, "menu-confirm-no"), cl -> openCategory(p, type, pg));
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }

    /** Compra y (según cosmetics.auto-equip) equipa. Envía el resultado al jugador. */
    void buy(Player p, Cosmetics.Item it) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) {
            disabled(p);
            return;
        }
        Component shown = c.render(it, p.getName());
        switch (c.buy(p, it)) {
            case OK -> {
                p.sendMessage(st().msg(p, "cos-bought", Placeholder.component("item", shown),
                        Placeholder.unparsed("price", plugin.eco().fmt(it.price()))));
                p.playSound(p.getLocation(), "entity.player.levelup", 0.7f, 1.3f);
                if (plugin.getConfig().getBoolean("cosmetics.auto-equip", true)) c.equip(p, it);
            }
            case NOT_ENOUGH -> p.sendMessage(st().msg(p, "cos-not-enough",
                    Placeholder.unparsed("price", plugin.eco().fmt(it.price())),
                    Placeholder.unparsed("balance", plugin.eco().fmt(plugin.eco().balance(p)))));
            case ALREADY -> p.sendMessage(st().msg(p, "cos-already"));
            case DISABLED -> p.sendMessage(st().msg(p, "cos-disabled"));
        }
    }

    // ================================================================ PANEL ADMIN / STAFF

    void openAdmin(Player p) {
        if (!p.hasPermission("sourcechat.mod")) {
            p.sendMessage(st().msg(p, "no-permission"));
            return;
        }
        new Gui(p, t(p, "menu-admin-title"), 6, g -> {
            boolean admin = p.hasPermission("sourcechat.admin");
            ChatState state = plugin.state();

            for (int i = 0; i < MODULES.length; i++) {
                String m = MODULES[i];
                boolean on = st().module(m);
                List<Component> lore = new ArrayList<>(st().lines(p, "module-" + m + "-desc"));
                lore.add(Component.empty());
                lore.add(t(p, on ? "menu-on" : "menu-off"));
                g.set(MODULE_SLOTS[i], Items.glow(Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE,
                        t(p, "module-" + m), lore), on), c -> {
                    if (!admin) {
                        p.sendMessage(st().msg(p, "no-permission"));
                        return;
                    }
                    click(p);
                    plugin.setModule(m, !on);
                    g.render();
                });
            }

            g.set(28, Items.glow(btn(p, Material.IRON_DOOR, "menu-admin-lock"), state.locked), c -> {
                state.locked = !state.locked;
                Bukkit.broadcast(st().msg(state.locked ? "lock-on" : "lock-off", Placeholder.unparsed("player", p.getName())));
                click(p);
                g.render();
            });
            g.set(29, btn(p, Material.CLOCK, "menu-admin-slow", Placeholder.unparsed("seconds", String.valueOf(state.slowSeconds))), c -> {
                int next = SLOW_STEPS[0];
                for (int i = 0; i < SLOW_STEPS.length; i++) {
                    if (SLOW_STEPS[i] == state.slowSeconds) {
                        next = SLOW_STEPS[(i + 1) % SLOW_STEPS.length];
                        break;
                    }
                }
                state.slowSeconds = next;
                Bukkit.broadcast(next == 0 ? st().msg("slow-off")
                        : st().msg("slow-on", Placeholder.unparsed("seconds", String.valueOf(next))));
                click(p);
                g.render();
            });
            g.set(30, btn(p, Material.BUCKET, "menu-admin-clear"), c -> {
                Component blank = Component.text("\n".repeat(100));
                for (Player o : Bukkit.getOnlinePlayers()) if (!o.hasPermission("sourcechat.bypass.clear")) o.sendMessage(blank);
                Bukkit.broadcast(st().msg("clearchat-done", Placeholder.unparsed("player", p.getName())));
            });
            boolean spying = state.spies.contains(p.getUniqueId());
            g.set(31, Items.glow(btn(p, Material.ENDER_EYE, "menu-admin-spy"), spying), c -> {
                if (!state.spies.remove(p.getUniqueId())) state.spies.add(p.getUniqueId());
                click(p);
                g.render();
            });
            g.set(32, btn(p, Material.FIREWORK_ROCKET, "menu-admin-gamestart"), c -> {
                if (plugin.games() == null) disabled(p);
                else p.sendMessage(st().msg(p, plugin.games().startGame(true) ? "game-started-admin" : "game-already"));
            });
            g.set(33, btn(p, Material.TNT, "menu-admin-gamestop"), c -> {
                if (plugin.games() == null) disabled(p);
                else p.sendMessage(st().msg(p, plugin.games().stopGame() ? "game-stopped" : "game-none"));
            });
            g.set(34, btn(p, Material.BELL, "menu-admin-broadcast"), c ->
                    plugin.prompts().ask(p, "prompt-broadcast", text ->
                            Bukkit.broadcast(st().fmt("broadcast-format", Placeholder.parsed("message", text)))));

            if (admin) {
                CType[] types = CType.values();
                for (int i = 0; i < types.length; i++) {
                    CType ty = types[i];
                    g.set(37 + i, btn(p, ty.icon, "menu-admin-quick",
                            Placeholder.unparsed("type", Styler.plain(st().raw(p, "type-" + ty.id), 30))), c -> {
                        click(p);
                        if (plugin.quick() == null) {
                            disabled(p);
                            return;
                        }
                        plugin.prompts().ask(p, ty.message ? "prompt-quick-msg" : "prompt-quick-name", text -> {
                            int n = plugin.quick().apply(ty, QuickSetup.split(ty, text));
                            p.sendMessage(st().msg(p, "quick-done", Placeholder.unparsed("count", String.valueOf(n)),
                                    Placeholder.unparsed("type", ty.id)));
                        }, Placeholder.unparsed("type", Styler.plain(st().raw(p, "type-" + ty.id), 30)));
                    });
                }
                g.set(49, btn(p, Material.REPEATER, "menu-admin-reload"), c -> {
                    plugin.reloadAll();
                    p.sendMessage(st().msg(p, "reloaded"));
                    openAdmin(p);
                });
            }
            back(g, p, 45, () -> openHub(p));
            g.set(53, btn(p, Material.BARRIER, "menu-hub-close"), c -> p.closeInventory());
        }).background(Material.BLACK_STAINED_GLASS_PANE).open();
    }
}
