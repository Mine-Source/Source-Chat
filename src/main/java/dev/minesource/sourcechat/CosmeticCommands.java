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

/**
 * Cosméticos y perfil: /cosmetics /chatcolor /namecolor /prefix /tag /joinmsg /quitmsg /jointitle
 * /nick /realname /coins /pay /lang.
 */
final class CosmeticCommands implements TabExecutor {

    private final SourceChat plugin;

    CosmeticCommands(SourceChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Settings st = plugin.settings();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "cosmetics" -> cosmetics(s, a);
            case "chatcolor", "namecolor", "prefix", "tag", "joinmsg", "quitmsg", "jointitle" ->
                    typeCommand(s, CType.parse(name), a);
            case "nick" -> nick(s, a);
            case "realname" -> realname(s, a);
            case "coins" -> coins(s, a);
            case "pay" -> pay(s, a);
            case "lang" -> lang(s, a);
            default -> {
                return false;
            }
        }
        return true;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(plugin.settings().msg(s, "only-players"));
        return null;
    }

    // ---------------------------------------------------------------- tienda

    private void cosmetics(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (plugin.cosmetics() == null) {
            p.sendMessage(plugin.settings().msg(p, "module-disabled"));
            return;
        }
        CType t = a.length > 0 ? CType.parse(a[0]) : null;
        if (t == null) plugin.menus().openShop(p);
        else plugin.menus().openCategory(p, t, 0);
    }

    /** /tag, /prefix... sin argumentos abre la categoría; con id equipa; "off" quita; "buy id" compra. */
    private void typeCommand(CommandSender s, CType type, String[] a) {
        Player p = player(s);
        if (p == null || type == null) return;
        Settings st = plugin.settings();
        Cosmetics c = plugin.cosmetics();
        if (c == null) {
            p.sendMessage(st.msg(p, "module-disabled"));
            return;
        }
        if (a.length == 0) {
            plugin.menus().openCategory(p, type, 0);
            return;
        }
        String first = a[0].toLowerCase(Locale.ROOT);
        if (first.equals("off") || first.equals("none") || first.equals("remove") || first.equals("quitar")) {
            c.unequip(p, type);
            p.sendMessage(st.msg(p, "cos-unequipped"));
            return;
        }
        boolean buy = first.equals("buy") || first.equals("comprar");
        if (buy && a.length < 2) {
            p.sendMessage(st.msg(p, "cos-type-usage", Placeholder.unparsed("cmd", type.id)));
            return;
        }
        Cosmetics.Item it = c.get(type, buy ? a[1] : a[0]);
        if (it == null || !it.enabled()) {
            p.sendMessage(st.msg(p, "cos-unknown"));
            return;
        }
        Profile pr = plugin.profiles().of(p);
        if (c.owns(p, pr, it)) {
            c.equip(p, it);
            p.sendMessage(st.msg(p, "cos-equipped", Placeholder.component("item", c.render(it, p.getName()))));
        } else if (buy) {
            plugin.menus().buy(p, it);
        } else {
            p.sendMessage(st.msg(p, "cos-not-owned", Placeholder.unparsed("cmd", type.id + " buy " + it.id())));
        }
    }

    // ---------------------------------------------------------------- apodos

    private void nick(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!s.hasPermission("sourcechat.nick")) {
            s.sendMessage(st.msg(s, "no-permission"));
            return;
        }
        if (a.length == 0) {
            if (s instanceof Player p) {
                Profile pr = plugin.profiles().of(p);
                s.sendMessage(st.msg(s, "nick-current", Placeholder.component("nick", plugin.names().name(p, pr))));
            }
            s.sendMessage(st.msg(s, "nick-usage"));
            return;
        }
        // /nick <jugador> <apodo|off> (admin)
        Player other = a.length >= 2 ? Bukkit.getPlayerExact(a[0]) : null;
        if (other != null && !other.equals(s)) {
            if (!s.hasPermission("sourcechat.nick.others")) {
                s.sendMessage(st.msg(s, "no-permission"));
                return;
            }
            String rest = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
            if (isOff(rest)) plugin.names().resetNick(s, other);
            else plugin.names().setNick(s, other, rest);
            return;
        }
        Player p = player(s);
        if (p == null) return;
        String text = String.join(" ", a);
        if (isOff(text)) plugin.names().resetNick(p, p);
        else plugin.names().setNick(p, p, text);
    }

    private static boolean isOff(String t) {
        String k = t.trim().toLowerCase(Locale.ROOT);
        return k.equals("off") || k.equals("reset") || k.equals("clear") || k.equals("quitar");
    }

    private void realname(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0) {
            s.sendMessage(st.msg(s, "realname-usage"));
            return;
        }
        Player t = plugin.names().byName(String.join(" ", a));
        if (t == null) {
            s.sendMessage(st.msg(s, "realname-none"));
            return;
        }
        s.sendMessage(st.msg(s, "realname-result", Placeholder.unparsed("input", String.join(" ", a)),
                Placeholder.unparsed("player", t.getName())));
    }

    // ---------------------------------------------------------------- monedas

    private void coins(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0) {
            Player p = player(s);
            if (p != null) {
                p.sendMessage(st.msg(p, "coins-balance", Placeholder.unparsed("coins", plugin.eco().fmt(plugin.eco().balance(p)))));
            }
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return;
        }
        s.sendMessage(st.msg(s, "coins-balance-other", Placeholder.unparsed("player", t.getName()),
                Placeholder.unparsed("coins", plugin.eco().fmt(plugin.eco().balance(t)))));
    }

    private void pay(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        Settings st = plugin.settings();
        if (a.length < 2) {
            p.sendMessage(st.msg(p, "pay-usage"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            p.sendMessage(st.msg(p, "player-not-found"));
            return;
        }
        if (t.equals(p)) {
            p.sendMessage(st.msg(p, "pay-self"));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(a[1]);
        } catch (NumberFormatException ex) {
            amount = -1;
        }
        if (amount <= 0) {
            p.sendMessage(st.msg(p, "pay-invalid"));
            return;
        }
        if (!plugin.eco().withdraw(p, amount)) {
            p.sendMessage(st.msg(p, "pay-notenough"));
            return;
        }
        plugin.eco().deposit(t, amount);
        p.sendMessage(st.msg(p, "pay-done", Placeholder.unparsed("player", t.getName()),
                Placeholder.unparsed("coins", plugin.eco().fmt(amount))));
        t.sendMessage(st.msg(t, "pay-received", Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("coins", plugin.eco().fmt(amount))));
    }

    // ---------------------------------------------------------------- idioma

    private void lang(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        Settings st = plugin.settings();
        if (a.length == 0) {
            plugin.menus().openLang(p);
            return;
        }
        String code = a[0].toLowerCase(Locale.ROOT);
        if (!plugin.lang().has(code)) {
            p.sendMessage(st.msg(p, "lang-unknown", Placeholder.unparsed("list", String.join(", ", plugin.lang().codes()))));
            return;
        }
        plugin.profiles().of(p).lang = code;
        plugin.profiles().touch();
        p.sendMessage(st.msg(p, "language-set", Placeholder.unparsed("language", Styler.plain(plugin.lang().meta(code).name(), 30))));
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        CType type = CType.parse(name);
        Cosmetics c = plugin.cosmetics();
        if (name.equals("cosmetics") && a.length == 1) {
            for (CType t : CType.values()) out.add(t.id);
        } else if (type != null && c != null && s instanceof Player p) {
            if (a.length == 1) {
                out.addAll(List.of("off", "buy"));
                for (Cosmetics.Item it : c.list(type, true)) out.add(it.id());
            } else if (a.length == 2 && a[0].equalsIgnoreCase("buy")) {
                for (Cosmetics.Item it : c.list(type, true)) out.add(it.id());
            }
        } else if (name.equals("lang") && a.length == 1) {
            out.addAll(plugin.lang().codes());
        } else if ((name.equals("pay") || name.equals("coins") || name.equals("realname")) && a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (name.equals("nick") && a.length == 1) {
            out.add("off");
            if (s.hasPermission("sourcechat.nick.others")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
