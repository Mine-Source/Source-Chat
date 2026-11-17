package dev.novachat;

import net.kyori.adventure.text.Component;
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
import java.util.UUID;

/** /novachat, /msg, /reply, /ignore. */
final class Commands implements TabExecutor {

    private final NovaChat plugin;

    Commands(NovaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        return switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "novachat" -> nova(sender, a);
            case "msg" -> msg(sender, a);
            case "reply" -> reply(sender, a);
            case "ignore" -> ignore(sender, a);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- /novachat

    private boolean nova(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            s.sendMessage(st.fmt("help"));
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (s instanceof Player p) plugin.menus().open(p);
                else s.sendMessage(st.msg("only-players"));
            }
            case "view" -> {
                if (s instanceof Player p && a.length > 1) plugin.tokens().open(p, a[1]);
            }
            case "reload" -> {
                if (!s.hasPermission("novachat.admin")) {
                    s.sendMessage(st.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                s.sendMessage(plugin.settings().msg("reloaded"));
            }
            case "game" -> {
                if (!s.hasPermission("novachat.admin")) {
                    s.sendMessage(st.msg("no-permission"));
                    return true;
                }
                String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                if (sub.equals("start")) {
                    s.sendMessage(st.msg(plugin.games().startGame(true) ? "game-started-admin" : "game-already"));
                } else if (sub.equals("stop")) {
                    s.sendMessage(st.msg(plugin.games().stopGame() ? "game-stopped" : "game-none"));
                } else {
                    s.sendMessage(st.fmt("help"));
                }
            }
            default -> s.sendMessage(st.fmt("help"));
        }
        return true;
    }

    // ---------------------------------------------------------------- mensajes privados

    private boolean msg(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 2) {
            s.sendMessage(st.msg("usage-msg"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            s.sendMessage(st.msg("player-not-found"));
            return true;
        }
        whisper(s, target, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
        return true;
    }

    private boolean reply(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg("only-players"));
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(st.msg("usage-reply"));
            return true;
        }
        UUID last = plugin.profiles().get(p.getUniqueId()).lastMsg;
        Player target = last == null ? null : Bukkit.getPlayer(last);
        if (target == null) {
            s.sendMessage(st.msg("reply-none"));
            return true;
        }
        whisper(s, target, String.join(" ", a));
        return true;
    }

    private void whisper(CommandSender s, Player target, String raw) {
        Settings st = plugin.settings();
        if (s instanceof Player p) {
            Profile mine = plugin.profiles().get(p.getUniqueId());
            if (mine.muted()) {
                s.sendMessage(st.muted(mine));
                return;
            }
            if (p.equals(target)) {
                s.sendMessage(st.msg("msg-self"));
                return;
            }
            boolean bypass = p.hasPermission("novachat.bypass.ignore");
            Profile tp = plugin.profiles().get(target.getUniqueId());
            if (!bypass && tp.ignored.contains(p.getUniqueId())) {
                s.sendMessage(st.msg("msg-ignored"));
                return;
            }
            if (!bypass && !tp.messages) {
                s.sendMessage(st.msg("msg-disabled"));
                return;
            }
        }
        Component body = Text.user(s, raw);
        target.sendMessage(st.fmt("pm-from",
                Placeholder.unparsed("player", s.getName()), Placeholder.component("message", body)));
        s.sendMessage(st.fmt("pm-to",
                Placeholder.unparsed("player", target.getName()), Placeholder.component("message", body)));
        for (UUID u : plugin.state().spies) {
            Player sp = Bukkit.getPlayer(u);
            if (sp != null && !sp.equals(target) && !sp.equals(s)) {
                sp.sendMessage(st.fmt("spy-pm",
                        Placeholder.unparsed("from", s.getName()),
                        Placeholder.unparsed("to", target.getName()),
                        Placeholder.component("message", body)));
            }
        }
        Profile tp = plugin.profiles().get(target.getUniqueId());
        if (tp.sounds) target.playSound(target.getLocation(), "entity.experience_orb.pickup", 0.8f, 1.6f);
        if (s instanceof Player p) {
            tp.lastMsg = p.getUniqueId();
            plugin.profiles().get(p.getUniqueId()).lastMsg = target.getUniqueId();
        }
    }

    // ---------------------------------------------------------------- /ignore

    private boolean ignore(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg("only-players"));
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(st.msg("usage-ignore"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            s.sendMessage(st.msg("player-not-found"));
            return true;
        }
        if (target.equals(p)) {
            s.sendMessage(st.msg("ignore-self"));
            return true;
        }
        Profile me = plugin.profiles().get(p.getUniqueId());
        UUID tid = target.getUniqueId();
        if (me.ignored.remove(tid)) {
            s.sendMessage(st.msg("ignore-off", Placeholder.unparsed("player", target.getName())));
        } else if (target.hasPermission("novachat.bypass.ignore")) {
            s.sendMessage(st.msg("ignore-bypass"));
            return true;
        } else {
            me.ignored.add(tid);
            s.sendMessage(st.msg("ignore-on", Placeholder.unparsed("player", target.getName())));
        }
        plugin.profiles().touch();
        return true;
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (name.equals("novachat")) {
            if (a.length == 1) {
                out.add("menu");
                out.add("help");
                if (s.hasPermission("novachat.admin")) {
                    out.add("reload");
                    out.add("game");
                }
            } else if (a.length == 2 && a[0].equalsIgnoreCase("game") && s.hasPermission("novachat.admin")) {
                out.add("start");
                out.add("stop");
            }
        } else if ((name.equals("msg") || name.equals("ignore")) && a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
