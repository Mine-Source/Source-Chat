package dev.minesource.sourcechat;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /sourcechat: menú, ayuda, recarga, módulos, configuración rápida, administración de cosméticos y monedas. */
final class AdminCommand implements TabExecutor {

    private final SourceChat plugin;

    AdminCommand(SourceChat plugin) {
        this.plugin = plugin;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("sourcechat.admin")) return true;
        s.sendMessage(plugin.settings().msg(s, "no-permission"));
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0) {
            if (s instanceof Player p) plugin.menus().openHub(p);
            else s.sendMessage(st.fmt(s, "help"));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (s instanceof Player p) plugin.menus().openHub(p);
                else s.sendMessage(st.msg(s, "only-players"));
            }
            case "lang" -> {
                if (s instanceof Player p) plugin.menus().openLang(p);
                else s.sendMessage(st.msg(s, "only-players"));
            }
            case "admin" -> {
                if (s instanceof Player p) plugin.menus().openAdmin(p);
                else s.sendMessage(st.msg(s, "only-players"));
            }
            case "view" -> {
                if (s instanceof Player p && a.length > 1) plugin.tokens().open(p, a[1]);
            }
            case "reload" -> {
                if (!admin(s)) return true;
                plugin.reloadAll();
                s.sendMessage(plugin.settings().msg(s, "reloaded"));
            }
            case "module" -> {
                if (admin(s)) module(s, a);
            }
            case "quick" -> {
                if (admin(s)) quick(s, a);
            }
            case "cosmetic" -> {
                if (admin(s)) cosmetic(s, a);
            }
            case "coins" -> {
                if (admin(s)) coins(s, a);
            }
            case "info" -> {
                if (admin(s)) info(s);
            }
            case "game" -> {
                if (!admin(s)) return true;
                if (plugin.games() == null) s.sendMessage(st.msg(s, "module-disabled"));
                else plugin.games().command(s, Arrays.copyOfRange(a, 1, a.length));
            }
            default -> s.sendMessage(st.fmt(s, "help"));
        }
        return true;
    }

    // ---------------------------------------------------------------- módulos

    private void module(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 3) {
            for (String m : SourceChat.MODULES) {
                s.sendMessage(st.msg(s, "module-line", Placeholder.unparsed("module", m),
                        Placeholder.parsed("state", st.raw(s, st.module(m) ? "state-on" : "state-off"))));
            }
            s.sendMessage(st.msg(s, "module-usage"));
            return;
        }
        String m = a[1].toLowerCase(Locale.ROOT);
        if (!SourceChat.MODULES.contains(m)) {
            s.sendMessage(st.msg(s, "module-unknown", Placeholder.unparsed("list", String.join(", ", SourceChat.MODULES))));
            return;
        }
        boolean on = switch (a[2].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "activar" -> true;
            case "off", "false", "disable", "desactivar" -> false;
            default -> !st.module(m);
        };
        plugin.setModule(m, on);
        s.sendMessage(plugin.settings().msg(s, "module-set", Placeholder.unparsed("module", m),
                Placeholder.parsed("state", plugin.settings().raw(s, on ? "state-on" : "state-off"))));
    }

    // ---------------------------------------------------------------- configuración rápida

    private void quick(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (plugin.quick() == null) {
            s.sendMessage(st.msg(s, "module-disabled"));
            return;
        }
        if (a.length < 2) {
            s.sendMessage(st.fmt(s, "quick-usage"));
            return;
        }
        if (a[1].equalsIgnoreCase("scan")) {
            Map<CType, Integer> r = plugin.quick().scan();
            int total = r.values().stream().mapToInt(Integer::intValue).sum();
            s.sendMessage(st.msg(s, "quick-scan-done", Placeholder.unparsed("count", String.valueOf(total))));
            return;
        }
        CType type = CType.parse(a[1]);
        if (type == null) {
            s.sendMessage(st.msg(s, "quick-invalid-type"));
            return;
        }
        if (a.length == 2) {
            if (s instanceof Player p) {
                plugin.prompts().ask(p, type.message ? "prompt-quick-msg" : "prompt-quick-name", text -> {
                    int n = plugin.quick().apply(type, QuickSetup.split(type, text));
                    p.sendMessage(plugin.settings().msg(p, "quick-done", Placeholder.unparsed("count", String.valueOf(n)),
                            Placeholder.unparsed("type", type.id)));
                }, Placeholder.unparsed("type", Styler.plain(st.raw(p, "type-" + type.id), 30)));
            } else {
                s.sendMessage(st.fmt(s, "quick-usage"));
            }
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        int n = plugin.quick().apply(type, QuickSetup.split(type, text));
        s.sendMessage(st.msg(s, "quick-done", Placeholder.unparsed("count", String.valueOf(n)),
                Placeholder.unparsed("type", type.id)));
    }

    // ---------------------------------------------------------------- cosméticos (manual y pruebas)

    private void cosmetic(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        Cosmetics c = plugin.cosmetics();
        if (c == null) {
            s.sendMessage(st.msg(s, "module-disabled"));
            return;
        }
        if (a.length < 2) {
            s.sendMessage(st.fmt(s, "cosa-usage"));
            return;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                CType t = a.length > 2 ? CType.parse(a[2]) : null;
                if (t == null) {
                    s.sendMessage(st.msg(s, "quick-invalid-type"));
                    return;
                }
                s.sendMessage(st.msg(s, "cosa-list-header", Placeholder.unparsed("type", t.id),
                        Placeholder.unparsed("count", String.valueOf(c.list(t, false).size()))));
                for (Cosmetics.Item it : c.list(t, false)) {
                    s.sendMessage(st.fmt(s, "cosa-list-line", Placeholder.unparsed("id", it.id()),
                            Placeholder.unparsed("price", String.valueOf(it.price())),
                            Placeholder.parsed("state", st.raw(s, it.enabled() ? "state-on" : "state-off")),
                            Placeholder.component("item", c.render(it, s.getName()))));
                }
            }
            case "add" -> add(s, a);
            case "remove" -> {
                CType t = a.length > 3 ? CType.parse(a[2]) : null;
                if (t == null) {
                    s.sendMessage(st.fmt(s, "cosa-usage"));
                    return;
                }
                s.sendMessage(st.msg(s, c.remove(t, a[3]) ? "cosa-removed" : "cos-unknown"));
            }
            case "price" -> {
                CType t = a.length > 4 ? CType.parse(a[2]) : null;
                if (t == null || !a[4].matches("\\d{1,9}")) {
                    s.sendMessage(st.fmt(s, "cosa-usage"));
                    return;
                }
                s.sendMessage(st.msg(s, c.setPrice(t, a[3], Long.parseLong(a[4])) ? "cosa-price" : "cos-unknown"));
            }
            case "toggle" -> {
                CType t = a.length > 3 ? CType.parse(a[2]) : null;
                if (t == null) {
                    s.sendMessage(st.fmt(s, "cosa-usage"));
                    return;
                }
                Boolean now = c.toggle(t, a[3]);
                if (now == null) s.sendMessage(st.msg(s, "cos-unknown"));
                else s.sendMessage(st.msg(s, "cosa-toggled", Placeholder.unparsed("id", a[3]),
                        Placeholder.parsed("state", st.raw(s, now ? "state-on" : "state-off"))));
            }
            case "give", "take", "test" -> giveTakeTest(s, a, sub);
            case "reset" -> {
                if (a.length < 3) {
                    s.sendMessage(st.fmt(s, "cosa-usage"));
                    return;
                }
                ProfileStore.Target t = plugin.profiles().target(a[2]);
                if (t == null) {
                    s.sendMessage(st.msg(s, "player-not-found"));
                    return;
                }
                Profile pr = plugin.profiles().get(t.id());
                pr.owned.clear();
                pr.equipped.clear();
                pr.testing.clear();
                plugin.profiles().touch();
                if (t.online() != null) plugin.names().apply(t.online());
                s.sendMessage(st.msg(s, "cosa-reset", Placeholder.unparsed("player", t.name())));
            }
            default -> s.sendMessage(st.fmt(s, "cosa-usage"));
        }
    }

    /** /sourcechat cosmetic add &lt;tipo&gt; &lt;id&gt; &lt;precio&gt; &lt;formato MiniMessage&gt; — configuración manual. */
    private void add(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        CType t = a.length > 5 ? CType.parse(a[2]) : null;
        if (t == null || !a[4].matches("\\d{1,9}")) {
            s.sendMessage(st.fmt(s, "cosa-usage"));
            return;
        }
        String id = Styler.slug(a[3]);
        String value = String.join(" ", Arrays.copyOfRange(a, 5, a.length));
        String sub = "";
        if (t == CType.JOINTITLE && value.contains(";")) {
            String[] ts = value.split("\\s*;\\s*", 2);
            value = ts[0];
            sub = ts[1];
        }
        long price = Long.parseLong(a[4]);
        Cosmetics.Item it = new Cosmetics.Item(t, id, a[3], value, sub, price, t.icon,
                price >= 2000 ? "legendary" : price >= 1000 ? "epic" : price >= 500 ? "rare" : "common", "", true);
        plugin.cosmetics().addAll(t, List.of(it));
        s.sendMessage(st.msg(s, "cosa-added", Placeholder.unparsed("id", id), Placeholder.unparsed("type", t.id)));
    }

    private void giveTakeTest(CommandSender s, String[] a, String sub) {
        Settings st = plugin.settings();
        Cosmetics c = plugin.cosmetics();
        CType t = a.length > 4 ? CType.parse(a[3]) : null;
        if (t == null) {
            s.sendMessage(st.fmt(s, "cosa-usage"));
            return;
        }
        ProfileStore.Target target = plugin.profiles().target(a[2]);
        if (target == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return;
        }
        Cosmetics.Item it = c.get(t, a[4]);
        if (it == null) {
            s.sendMessage(st.msg(s, "cos-unknown"));
            return;
        }
        Profile pr = plugin.profiles().get(target.id());
        switch (sub) {
            case "give" -> {
                pr.owned.add(it.key());
                plugin.profiles().touch();
                s.sendMessage(st.msg(s, "cosa-given", Placeholder.unparsed("id", it.id()), Placeholder.unparsed("player", target.name())));
                if (target.online() != null) {
                    target.online().sendMessage(st.msg(target.online(), "cosa-received",
                            Placeholder.component("item", c.render(it, target.name()))));
                }
            }
            case "take" -> {
                pr.owned.remove(it.key());
                pr.testing.remove(it.key());
                if (it.id().equals(pr.equipped.get(t.id))) pr.equipped.remove(t.id);
                plugin.profiles().touch();
                if (target.online() != null) plugin.names().apply(target.online());
                s.sendMessage(st.msg(s, "cosa-taken", Placeholder.unparsed("id", it.id()), Placeholder.unparsed("player", target.name())));
            }
            default -> { // test: equipa sin comprar (fuera de producción)
                Player online = target.online();
                if (online == null) {
                    s.sendMessage(st.msg(s, "player-not-found"));
                    return;
                }
                pr.testing.add(it.key());
                c.equip(online, it);
                c.preview(online, it);
                s.sendMessage(st.msg(s, "cosa-test", Placeholder.unparsed("id", it.id()), Placeholder.unparsed("player", online.getName())));
            }
        }
    }

    // ---------------------------------------------------------------- monedas

    private void coins(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 3) {
            s.sendMessage(st.fmt(s, "coins-usage"));
            return;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        ProfileStore.Target t = plugin.profiles().target(a[2]);
        if (t == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return;
        }
        Profile pr = plugin.profiles().get(t.id());
        if (sub.equals("balance")) {
            long bal = t.online() != null ? plugin.eco().balance(t.online()) : pr.coins;
            s.sendMessage(st.msg(s, "coins-balance-other", Placeholder.unparsed("player", t.name()),
                    Placeholder.unparsed("coins", plugin.eco().fmt(bal))));
            return;
        }
        if (a.length < 4 || !a[3].matches("\\d{1,12}")) {
            s.sendMessage(st.fmt(s, "coins-usage"));
            return;
        }
        long amount = Long.parseLong(a[3]);
        switch (sub) {
            case "give" -> {
                if (t.online() != null) plugin.eco().deposit(t.online(), amount);
                else synchronized (pr) {
                    pr.coins += amount;
                }
            }
            case "take" -> {
                if (t.online() != null) plugin.eco().withdraw(t.online(), amount);
                else synchronized (pr) {
                    pr.coins = Math.max(0, pr.coins - amount);
                }
            }
            case "set" -> {
                synchronized (pr) {
                    pr.coins = amount;
                }
            }
            default -> {
                s.sendMessage(st.fmt(s, "coins-usage"));
                return;
            }
        }
        plugin.profiles().touch();
        s.sendMessage(st.msg(s, "coins-changed", Placeholder.unparsed("player", t.name()),
                Placeholder.unparsed("coins", plugin.eco().fmt(amount))));
    }

    // ---------------------------------------------------------------- info

    private void info(CommandSender s) {
        Settings st = plugin.settings();
        s.sendMessage(st.fmt(s, "info",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()),
                Placeholder.unparsed("identity", plugin.profiles().nameMode() ? "name (offline)" : "uuid (online)"),
                Placeholder.unparsed("language", plugin.lang().def()),
                Placeholder.unparsed("languages", String.join(", ", plugin.lang().codes())),
                Placeholder.unparsed("economy", plugin.eco().providerName()),
                Placeholder.unparsed("papi", String.valueOf(plugin.papi())),
                Placeholder.unparsed("modules", String.join(", ", SourceChat.MODULES.stream().filter(st::module).toList()))));
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean adm = s.hasPermission("sourcechat.admin");
        if (a.length == 1) {
            out.addAll(List.of("menu", "help", "lang"));
            if (s.hasPermission("sourcechat.mod")) out.add("admin");
            if (adm) out.addAll(List.of("reload", "module", "quick", "cosmetic", "coins", "info", "game"));
        } else if (adm) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("module")) {
                if (a.length == 2) out.addAll(SourceChat.MODULES);
                else if (a.length == 3) out.addAll(List.of("on", "off", "toggle"));
            } else if (sub.equals("quick")) {
                if (a.length == 2) {
                    out.add("scan");
                    for (CType t : CType.values()) out.add(t.id);
                }
            } else if (sub.equals("cosmetic")) {
                cosmeticTab(a, out);
            } else if (sub.equals("coins")) {
                if (a.length == 2) out.addAll(List.of("give", "take", "set", "balance"));
                else if (a.length == 3) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (sub.equals("game") && plugin.games() != null) {
                out.addAll(plugin.games().tab(s, Arrays.copyOfRange(a, 1, a.length)));
            }
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void cosmeticTab(String[] a, List<String> out) {
        if (a.length == 2) {
            out.addAll(List.of("list", "add", "remove", "price", "toggle", "give", "take", "test", "reset"));
            return;
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        boolean player = sub.equals("give") || sub.equals("take") || sub.equals("test") || sub.equals("reset");
        if (a.length == 3) {
            if (player) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else for (CType t : CType.values()) out.add(t.id);
        } else if (a.length == 4 && player && !sub.equals("reset")) {
            for (CType t : CType.values()) out.add(t.id);
        } else if (plugin.cosmetics() != null) {
            CType t = CType.parse(a[player ? 3 : 2]);
            boolean idSlot = player ? a.length == 5 : (a.length == 4 && !sub.equals("add") && !sub.equals("list"));
            if (t != null && idSlot) for (Cosmetics.Item it : plugin.cosmetics().list(t, false)) out.add(it.id());
        }
    }
}
