package dev.minesource.sourcechat;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Anuncios automáticos y manuales: chat (varias líneas), action bar, título y bossbar. Cada anuncio puede
 * limitarse por permiso ("!permiso" = solo quien NO lo tiene) y por mundos. Se administran con /announce
 * y se guardan en announcements.yml.
 */
final class Announcer {

    private static final List<String> TYPES = List.of("chat", "actionbar", "title", "bossbar");

    record Ann(String id, String type, List<String> lines, String permission, List<String> worlds,
               String sound, int duration, String color, boolean enabled) {
        Ann withEnabled(boolean e) {
            return new Ann(id, type, lines, permission, worlds, sound, duration, color, e);
        }
    }

    private final SourceChat plugin;
    private final File file;
    private final Map<String, Ann> anns = new LinkedHashMap<>();
    private BukkitTask task;
    private int index;
    private boolean enabled = true;
    private boolean random;
    private int interval = 300;
    private int minPlayers = 1;

    Announcer(SourceChat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "announcements.yml");
    }

    void start() {
        if (!file.exists()) writeDefaults();
        load();
        schedule();
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void schedule() {
        stop();
        if (!enabled) return;
        long t = Math.max(5, interval) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, t, t);
    }

    // ------------------------------------------------------------ archivo

    private void writeDefaults() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("SourceChat · anuncios",
                "Administra con /announce (list, add, remove, toggle, now, send, interval, random, on, off).",
                "type: chat | actionbar | title | bossbar · permission: \"perm\" o \"!perm\" · worlds: [] = todos"));
        y.set("settings.enabled", true);
        y.set("settings.interval", 300);
        y.set("settings.random", false);
        y.set("settings.min-players", 1);
        y.set("announcements.tips.type", "chat");
        y.set("announcements.tips.lines", List.of("<gray>Usa <white>[item]</white> para mostrar lo que llevas en la mano.",
                "<gray>Cambia tus preferencias con <white>/sourcechat</white>."));
        y.set("announcements.shop.type", "actionbar");
        y.set("announcements.shop.lines", List.of("<gradient:#8B5CF6:#22D3EE>Personaliza tu chat en /cosmetics</gradient>"));
        y.set("announcements.welcome-title.type", "title");
        y.set("announcements.welcome-title.lines", List.of("<gradient:#8B5CF6:#22D3EE><bold>SourceChat</bold></gradient>", "<gray>Hola, <player>"));
        y.set("announcements.welcome-title.enabled", false);
        y.set("announcements.event-bar.type", "bossbar");
        y.set("announcements.event-bar.lines", List.of("<gold>¡Evento de chat pronto!"));
        y.set("announcements.event-bar.duration", 8);
        y.set("announcements.event-bar.color", "YELLOW");
        y.set("announcements.event-bar.enabled", false);
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo crear announcements.yml: " + ex.getMessage());
        }
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        enabled = y.getBoolean("settings.enabled", true);
        interval = Math.max(5, y.getInt("settings.interval", 300));
        random = y.getBoolean("settings.random", false);
        minPlayers = Math.max(0, y.getInt("settings.min-players", 1));
        anns.clear();
        ConfigurationSection sec = y.getConfigurationSection("announcements");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection x = sec.getConfigurationSection(id);
            if (x == null) continue;
            String type = x.getString("type", "chat").toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) type = "chat";
            List<String> lines = x.getStringList("lines");
            if (lines.isEmpty()) continue;
            anns.put(id.toLowerCase(Locale.ROOT), new Ann(id.toLowerCase(Locale.ROOT), type, lines,
                    x.getString("permission", ""), x.getStringList("worlds"), x.getString("sound", ""),
                    Math.max(1, x.getInt("duration", 5)), x.getString("color", "PURPLE"), x.getBoolean("enabled", true)));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("SourceChat · anuncios"));
        y.set("settings.enabled", enabled);
        y.set("settings.interval", interval);
        y.set("settings.random", random);
        y.set("settings.min-players", minPlayers);
        for (Ann a : anns.values()) {
            String b = "announcements." + a.id() + ".";
            y.set(b + "type", a.type());
            y.set(b + "lines", a.lines());
            if (!a.permission().isEmpty()) y.set(b + "permission", a.permission());
            if (!a.worlds().isEmpty()) y.set(b + "worlds", a.worlds());
            if (!a.sound().isEmpty()) y.set(b + "sound", a.sound());
            y.set(b + "duration", a.duration());
            y.set(b + "color", a.color());
            y.set(b + "enabled", a.enabled());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar announcements.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ envío

    private void tick() {
        if (Bukkit.getOnlinePlayers().size() < Math.max(1, minPlayers)) return;
        List<Ann> active = new ArrayList<>();
        for (Ann a : anns.values()) if (a.enabled()) active.add(a);
        if (active.isEmpty()) return;
        Ann next = random ? active.get(ThreadLocalRandom.current().nextInt(active.size())) : active.get(index++ % active.size());
        deliver(next);
    }

    void deliver(Ann a) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!a.permission().isEmpty()) {
                boolean neg = a.permission().startsWith("!");
                boolean has = p.hasPermission(neg ? a.permission().substring(1) : a.permission());
                if (neg == has) continue;
            }
            if (!a.worlds().isEmpty() && !a.worlds().contains(p.getWorld().getName())) continue;
            send(p, a);
        }
    }

    private Component line(Player p, String raw, boolean prefix) {
        String t = plugin.papi() ? PapiHook.apply(p, raw) : raw;
        t = Text.legacyToMini(t);
        if (prefix) t = plugin.getConfig().getString("announcements.prefix", "") + t;
        return Text.MM.deserialize(t, Placeholder.parsed("player", p.getName()));
    }

    private void send(Player p, Ann a) {
        List<String> l = a.lines();
        switch (a.type()) {
            case "actionbar" -> p.sendActionBar(line(p, l.get(0), false));
            case "title" -> p.showTitle(Title.title(line(p, l.get(0), false),
                    l.size() > 1 ? line(p, l.get(1), false) : Component.empty(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(a.duration()), Duration.ofMillis(800))));
            case "bossbar" -> {
                BossBar.Color color;
                try {
                    color = BossBar.Color.valueOf(a.color().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    color = BossBar.Color.PURPLE;
                }
                BossBar bar = BossBar.bossBar(line(p, l.get(0), false), 1f, color, BossBar.Overlay.PROGRESS);
                p.showBossBar(bar);
                Bukkit.getScheduler().runTaskLater(plugin, () -> p.hideBossBar(bar), a.duration() * 20L);
            }
            default -> {
                for (int i = 0; i < l.size(); i++) p.sendMessage(line(p, l.get(i), i == 0));
            }
        }
        if (!a.sound().isEmpty()) p.playSound(p.getLocation(), a.sound(), 0.8f, 1.2f);
    }

    // ------------------------------------------------------------ comandos

    /** /announce list | now &lt;id&gt; | send &lt;tipo&gt; &lt;texto&gt; | add &lt;id&gt; &lt;tipo&gt; &lt;texto&gt; | remove | toggle | interval | random | on | off | reload. Líneas separadas por ||. */
    void command(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        if (!s.hasPermission("sourcechat.announce")) {
            s.sendMessage(st.msg(s, "no-permission"));
            return;
        }
        if (a.length == 0) {
            s.sendMessage(st.fmt(s, "ann-usage"));
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                s.sendMessage(st.msg(s, "ann-list-header", Placeholder.unparsed("count", String.valueOf(anns.size())),
                        Placeholder.unparsed("interval", String.valueOf(interval)),
                        Placeholder.parsed("state", st.raw(s, enabled ? "state-on" : "state-off"))));
                for (Ann an : anns.values()) {
                    s.sendMessage(st.fmt(s, "ann-list-line", Placeholder.unparsed("id", an.id()),
                            Placeholder.unparsed("type", an.type()),
                            Placeholder.parsed("state", st.raw(s, an.enabled() ? "state-on" : "state-off")),
                            Placeholder.unparsed("preview", Styler.plain(an.lines().get(0), 40))));
                }
            }
            case "now" -> {
                Ann an = a.length > 1 ? anns.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (an == null) {
                    s.sendMessage(st.msg(s, "ann-unknown"));
                    return;
                }
                deliver(an);
                s.sendMessage(st.msg(s, "ann-sent"));
            }
            case "send" -> {
                if (a.length < 3 || !TYPES.contains(a[1].toLowerCase(Locale.ROOT))) {
                    s.sendMessage(st.fmt(s, "ann-usage"));
                    return;
                }
                deliver(temp(a[1].toLowerCase(Locale.ROOT), join(a, 2)));
                s.sendMessage(st.msg(s, "ann-sent"));
            }
            case "add" -> {
                if (a.length < 4 || !TYPES.contains(a[2].toLowerCase(Locale.ROOT))) {
                    s.sendMessage(st.fmt(s, "ann-usage"));
                    return;
                }
                String id = Styler.slug(a[1]);
                Ann base = temp(a[2].toLowerCase(Locale.ROOT), join(a, 3));
                anns.put(id, new Ann(id, base.type(), base.lines(), "", List.of(), "", 5, "PURPLE", true));
                save();
                s.sendMessage(st.msg(s, "ann-added", Placeholder.unparsed("id", id)));
            }
            case "remove" -> {
                boolean ok = a.length > 1 && anns.remove(a[1].toLowerCase(Locale.ROOT)) != null;
                if (ok) save();
                s.sendMessage(st.msg(s, ok ? "ann-removed" : "ann-unknown"));
            }
            case "toggle" -> {
                Ann an = a.length > 1 ? anns.get(a[1].toLowerCase(Locale.ROOT)) : null;
                if (an == null) {
                    s.sendMessage(st.msg(s, "ann-unknown"));
                    return;
                }
                anns.put(an.id(), an.withEnabled(!an.enabled()));
                save();
                s.sendMessage(st.msg(s, "ann-toggled", Placeholder.unparsed("id", an.id()),
                        Placeholder.parsed("state", st.raw(s, !an.enabled() ? "state-on" : "state-off"))));
            }
            case "interval" -> {
                if (a.length < 2 || !a[1].matches("\\d{1,6}")) {
                    s.sendMessage(st.fmt(s, "ann-usage"));
                    return;
                }
                interval = Math.max(5, Integer.parseInt(a[1]));
                save();
                schedule();
                s.sendMessage(st.msg(s, "ann-interval", Placeholder.unparsed("seconds", String.valueOf(interval))));
            }
            case "random" -> {
                random = a.length > 1 ? a[1].equalsIgnoreCase("on") : !random;
                save();
                s.sendMessage(st.msg(s, "ann-random", Placeholder.parsed("state", st.raw(s, random ? "state-on" : "state-off"))));
            }
            case "on", "off" -> {
                enabled = sub.equals("on");
                save();
                schedule();
                s.sendMessage(st.msg(s, "ann-global", Placeholder.parsed("state", st.raw(s, enabled ? "state-on" : "state-off"))));
            }
            case "reload" -> {
                load();
                schedule();
                s.sendMessage(st.msg(s, "ann-reloaded"));
            }
            default -> s.sendMessage(st.fmt(s, "ann-usage"));
        }
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private static Ann temp(String type, String text) {
        List<String> lines = new ArrayList<>();
        for (String x : text.split("\\s*\\|\\|\\s*")) if (!x.isBlank()) lines.add(x.trim());
        if (lines.isEmpty()) lines.add(text);
        return new Ann("temp", type, lines, "", List.of(), "", 5, "PURPLE", true);
    }

    List<String> tab(CommandSender s, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("sourcechat.announce")) return out;
        if (a.length == 1) {
            out.addAll(List.of("list", "now", "send", "add", "remove", "toggle", "interval", "random", "on", "off", "reload"));
        } else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("now") || sub.equals("remove") || sub.equals("toggle")) out.addAll(anns.keySet());
            else if (sub.equals("send")) out.addAll(TYPES);
            else if (sub.equals("random")) out.addAll(List.of("on", "off"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("add")) {
            out.addAll(TYPES);
        }
        return out;
    }
}
