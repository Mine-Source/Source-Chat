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
import java.util.UUID;

/**
 * Moderación y canales: /mute /unmute /clearchat /lockchat /slowmode /chatspy /channel /broadcast
 * /staffchat /announce /chatgame.
 */
final class ModCommands implements TabExecutor {

    private static final String MOD = "sourcechat.mod";

    private final SourceChat plugin;

    ModCommands(SourceChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Settings st = plugin.settings();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        boolean needsMod = switch (name) {
            case "mute", "unmute", "clearchat", "lockchat", "slowmode", "broadcast" -> true;
            default -> false;
        };
        if (needsMod && !s.hasPermission(MOD)) {
            s.sendMessage(st.msg(s, "no-permission"));
            return true;
        }
        switch (name) {
            case "mute" -> mute(s, a);
            case "unmute" -> unmute(s, a);
            case "clearchat" -> clear(s);
            case "lockchat" -> lock(s);
            case "slowmode" -> slow(s, a);
            case "chatspy" -> spy(s);
            case "channel" -> channel(s, a);
            case "broadcast" -> broadcast(s, a);
            case "staffchat" -> staff(s, a);
            case "announce" -> {
                if (plugin.announcer() == null) s.sendMessage(st.msg(s, "module-disabled"));
                else plugin.announcer().command(s, a);
            }
            case "chatgame" -> {
                if (plugin.games() == null) s.sendMessage(st.msg(s, "module-disabled"));
                else plugin.games().command(s, a);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- silencios

    private void mute(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 1) {
            s.sendMessage(st.msg(s, "usage-mute"));
            return;
        }
        ProfileStore.Target t = plugin.profiles().target(a[0]);
        if (t == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return;
        }
        long ms = 0;
        int idx = 1;
        if (a.length > 1) {
            long d = Durations.parse(a[1]);
            if (d >= 0) {
                ms = d;
                idx = 2;
            } else if (Character.isDigit(a[1].charAt(0))) {
                s.sendMessage(st.msg(s, "usage-mute"));
                return;
            }
        }
        String reason = idx < a.length ? String.join(" ", Arrays.copyOfRange(a, idx, a.length)) : st.raw(s, "mute-default-reason");
        Profile pr = plugin.profiles().get(t.id());
        pr.muteUntil = ms == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ms;
        pr.muteReason = reason;
        plugin.profiles().touch();

        s.sendMessage(st.msg(s, "mute-done", Placeholder.unparsed("player", t.name()),
                Placeholder.parsed("time", ms == 0 ? st.raw(s, "time-permanent") : Durations.format(ms))));
        if (t.online() != null) t.online().sendMessage(st.muted(t.online(), pr));
    }

    private void unmute(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 1) {
            s.sendMessage(st.msg(s, "usage-unmute"));
            return;
        }
        ProfileStore.Target t = plugin.profiles().target(a[0]);
        if (t == null) {
            s.sendMessage(st.msg(s, "player-not-found"));
            return;
        }
        Profile pr = plugin.profiles().get(t.id());
        if (!pr.muted()) {
            s.sendMessage(st.msg(s, "not-muted", Placeholder.unparsed("player", t.name())));
            return;
        }
        pr.muteUntil = 0;
        pr.muteReason = "";
        plugin.profiles().touch();
        s.sendMessage(st.msg(s, "unmute-done", Placeholder.unparsed("player", t.name())));
        if (t.online() != null) t.online().sendMessage(st.msg(t.online(), "unmute-notify"));
    }

    // ---------------------------------------------------------------- estado del chat

    private void clear(CommandSender s) {
        Component blank = Component.text("\n".repeat(100));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("sourcechat.bypass.clear")) p.sendMessage(blank);
        }
        Bukkit.broadcast(plugin.settings().msg("clearchat-done", Placeholder.unparsed("player", s.getName())));
    }

    private void lock(CommandSender s) {
        ChatState state = plugin.state();
        state.locked = !state.locked;
        Bukkit.broadcast(plugin.settings().msg(state.locked ? "lock-on" : "lock-off",
                Placeholder.unparsed("player", s.getName())));
    }

    private void slow(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length < 1) {
            s.sendMessage(st.msg(s, "usage-slowmode"));
            return;
        }
        int secs;
        if (a[0].equalsIgnoreCase("off")) {
            secs = 0;
        } else {
            try {
                secs = Integer.parseInt(a[0]);
            } catch (NumberFormatException ex) {
                s.sendMessage(st.msg(s, "usage-slowmode"));
                return;
            }
        }
        secs = Math.max(0, Math.min(secs, 3600));
        plugin.state().slowSeconds = secs;
        if (secs == 0) {
            Bukkit.broadcast(st.msg("slow-off"));
        } else {
            Bukkit.broadcast(st.msg("slow-on", Placeholder.unparsed("seconds", String.valueOf(secs))));
        }
    }

    private void spy(CommandSender s) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p) || !p.hasPermission("sourcechat.spy")) {
            s.sendMessage(st.msg(s, s instanceof Player ? "no-permission" : "only-players"));
            return;
        }
        UUID id = p.getUniqueId();
        if (plugin.state().spies.remove(id)) {
            p.sendMessage(st.msg(p, "spy-off"));
        } else {
            plugin.state().spies.add(id);
            p.sendMessage(st.msg(p, "spy-on"));
        }
    }

    // ---------------------------------------------------------------- canales y staff chat

    private void channel(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!(s instanceof Player p)) {
            s.sendMessage(st.msg(s, "only-players"));
            return;
        }
        Profile pr = plugin.profiles().of(p);
        if (a.length == 0) {
            List<String> usable = new ArrayList<>();
            for (Channel c : st.channels().values()) if (c.canUse(p)) usable.add(c.id());
            Channel cur = st.channel(pr.channel);
            p.sendMessage(st.msg(p, "channel-list",
                    Placeholder.unparsed("current", cur.id()),
                    Placeholder.unparsed("list", String.join(", ", usable))));
            return;
        }
        Channel target = st.channels().get(a[0].toLowerCase(Locale.ROOT));
        if (target == null || !target.canUse(p)) {
            p.sendMessage(st.msg(p, "channel-unknown"));
            return;
        }
        pr.channel = target.id().equals(st.defaultChannel()) ? null : target.id();
        plugin.profiles().touch();
        p.sendMessage(st.msg(p, "channel-set", Placeholder.unparsed("channel", target.id())));
    }

    /** /staffchat: sin argumentos alterna el canal de staff; con texto envía un mensaje directo al staff. */
    private void staff(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!s.hasPermission("sourcechat.staffchat")) {
            s.sendMessage(st.msg(s, "no-permission"));
            return;
        }
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage(st.msg(s, "only-players"));
                return;
            }
            String id = plugin.getConfig().getString("staffchat.channel", "staff").toLowerCase(Locale.ROOT);
            Channel ch = st.channels().get(id);
            if (ch == null) {
                p.sendMessage(st.msg(p, "staffchat-nochannel"));
                return;
            }
            Profile pr = plugin.profiles().of(p);
            if (id.equals(pr.channel)) {
                pr.channel = null;
                p.sendMessage(st.msg(p, "staffchat-off"));
            } else {
                pr.channel = id;
                p.sendMessage(st.msg(p, "staffchat-on"));
            }
            plugin.profiles().touch();
            return;
        }
        Component body = Text.user(s, String.join(" ", a));
        Component from = s instanceof Player sp ? plugin.names().name(sp, plugin.profiles().of(sp)) : Component.text(s.getName());
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.hasPermission("sourcechat.staffchat")) {
                o.sendMessage(st.fmt(o, "staffchat-format", Placeholder.component("player", from),
                        Placeholder.component("message", body)));
            }
        }
        Bukkit.getConsoleSender().sendMessage(st.fmt("staffchat-format", Placeholder.component("player", from),
                Placeholder.component("message", body)));
    }

    private void broadcast(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (a.length == 0) {
            s.sendMessage(st.msg(s, "usage-broadcast"));
            return;
        }
        Bukkit.broadcast(st.fmt("broadcast-format", Placeholder.parsed("message", String.join(" ", a))));
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if ((name.equals("mute") || name.equals("unmute")) && a.length == 1) {
            if (s.hasPermission(MOD)) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (name.equals("mute") && a.length == 2) {
            out.addAll(List.of("10m", "1h", "1d", "perm"));
        } else if (name.equals("slowmode") && a.length == 1) {
            out.addAll(List.of("off", "3", "5", "10"));
        } else if (name.equals("channel") && a.length == 1 && s instanceof Player p) {
            for (Channel c : plugin.settings().channels().values()) if (c.canUse(p)) out.add(c.id());
        } else if (name.equals("announce") && plugin.announcer() != null) {
            out.addAll(plugin.announcer().tab(s, a));
        } else if (name.equals("chatgame") && plugin.games() != null) {
            out.addAll(plugin.games().tab(s, a));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
