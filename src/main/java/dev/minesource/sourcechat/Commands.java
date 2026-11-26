package dev.minesource.sourcechat;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sociales: /msg, /reply, /ignore, /togglemsg, /helpop. */
final class Commands implements TabExecutor {

    private final SourceChat plugin;
    private final Map<UUID, Long> helpopLast = new ConcurrentHashMap<>();

    Commands(SourceChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        return switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "msg" -> msg(s, a);
            case "reply" -> reply(s, a);
            case "ignore" -> ignore(s, a);
            case "togglemsg" -> toggleMsg(s);
            case "helpop" -> helpop(s, a);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- mensajes privados

    private boolean msg(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 2) {
            s.sendMessage(st.msg(s, "usage-msg"));
            return true;
        }
        Player target = plugin.names().byName(a[0]);
        if (target == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return true;
        }
        whisper(s, target, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
        return true;
    }

    private boolean reply(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg(s, "only-players"));
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(st.msg(s, "usage-reply"));
            return true;
        }
        UUID last = plugin.profiles().of(p).lastMsg;
        Player target = last == null ? null : Bukkit.getPlayer(last);
        if (target == null) {
            s.sendMessage(st.msg(s, "reply-none"));
            return true;
        }
        whisper(s, target, String.join(" ", a));
        return true;
    }

    private void whisper(CommandSender s, Player target, String raw) {
        Settings st = plugin.settings();
        if (s instanceof Player p) {
            Profile mine = plugin.profiles().of(p);
            if (mine.muted()) {
                s.sendMessage(st.muted(s, mine));
                return;
            }
            if (p.equals(target)) {
                s.sendMessage(st.msg(s, "msg-self"));
                return;
            }
            boolean bypass = p.hasPermission("sourcechat.bypass.ignore");
            Profile tp = plugin.profiles().of(target);
            if (!bypass && tp.ignored.contains(plugin.profiles().id(p))) {
                s.sendMessage(st.msg(s, "msg-ignored"));
                return;
            }
            if (!bypass && !tp.messages) {
                s.sendMessage(st.msg(s, "msg-disabled"));
                return;
            }
        }
        Component body = Text.user(s, raw);
        Component from = s instanceof Player sp ? plugin.names().name(sp, plugin.profiles().of(sp)) : Component.text(s.getName());
        Component to = plugin.names().name(target, plugin.profiles().of(target));
        target.sendMessage(st.fmt(target, "pm-from", Placeholder.component("player", from), Placeholder.component("message", body)));
        s.sendMessage(st.fmt(s, "pm-to", Placeholder.component("player", to), Placeholder.component("message", body)));
        for (UUID u : plugin.state().spies) {
            Player sp = Bukkit.getPlayer(u);
            if (sp != null && !sp.equals(target) && !sp.equals(s)) {
                sp.sendMessage(st.fmt(sp, "spy-pm",
                        Placeholder.unparsed("from", s.getName()),
                        Placeholder.unparsed("to", target.getName()),
                        Placeholder.component("message", body)));
            }
        }
        Profile tp = plugin.profiles().of(target);
        if (tp.sounds) target.playSound(target.getLocation(), "entity.experience_orb.pickup", 0.8f, 1.6f);
        if (s instanceof Player p) {
            tp.lastMsg = p.getUniqueId();
            plugin.profiles().of(p).lastMsg = target.getUniqueId();
        }
    }

    // ---------------------------------------------------------------- /ignore y /togglemsg

    private boolean ignore(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg(s, "only-players"));
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(st.msg(s, "usage-ignore"));
            return true;
        }
        Player target = plugin.names().byName(a[0]);
        if (target == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return true;
        }
        if (target.equals(p)) {
            s.sendMessage(st.msg(s, "ignore-self"));
            return true;
        }
        Profile me = plugin.profiles().of(p);
        UUID tid = plugin.profiles().id(target);
        if (me.ignored.remove(tid)) {
            s.sendMessage(st.msg(s, "ignore-off", Placeholder.unparsed("player", target.getName())));
        } else if (target.hasPermission("sourcechat.bypass.ignore")) {
            s.sendMessage(st.msg(s, "ignore-bypass"));
            return true;
        } else {
            me.ignored.add(tid);
            s.sendMessage(st.msg(s, "ignore-on", Placeholder.unparsed("player", target.getName())));
        }
        plugin.profiles().touch();
        return true;
    }

    private boolean toggleMsg(CommandSender s) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg(s, "only-players"));
            return true;
        }
        Profile pr = plugin.profiles().of(p);
        pr.messages = !pr.messages;
        plugin.profiles().touch();
        s.sendMessage(st.msg(s, pr.messages ? "togglemsg-on" : "togglemsg-off"));
        return true;
    }

    // ---------------------------------------------------------------- /helpop

    private boolean helpop(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0) {
            s.sendMessage(st.msg(s, "usage-helpop"));
            return true;
        }
        if (s instanceof Player p) {
            long wait = Math.max(0, plugin.getConfig().getLong("helpop.cooldown-seconds", 30)) * 1000L;
            long now = System.currentTimeMillis();
            Long last = helpopLast.get(p.getUniqueId());
            if (last != null && now - last < wait && !p.hasPermission("sourcechat.bypass.filter")) {
                s.sendMessage(st.msg(s, "helpop-cooldown",
                        Placeholder.unparsed("seconds", String.valueOf((wait - (now - last)) / 1000 + 1))));
                return true;
            }
            helpopLast.put(p.getUniqueId(), now);
        }
        Component body = Text.user(s, String.join(" ", a));
        int sent = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasPermission("sourcechat.helpop.receive") && !o.equals(s)) {
                o.sendMessage(st.fmt(o, "helpop-format", Placeholder.unparsed("player", s.getName()),
                        Placeholder.component("message", body)));
                o.playSound(o.getLocation(), "block.note_block.pling", 1f, 1.5f);
                sent++;
            }
        }
        s.sendMessage(st.msg(s, sent > 0 ? "helpop-sent" : "helpop-nostaff"));
        return true;
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if ((name.equals("msg") || name.equals("ignore")) && a.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
