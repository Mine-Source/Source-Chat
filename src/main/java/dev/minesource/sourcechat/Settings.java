package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Valores de config cacheados (lectura rápida y segura desde el hilo asíncrono del chat) + textos traducidos. */
final class Settings {

    record Format(String permission, String format) {
    }

    /** Placeholder personalizado: texto del chat que se convierte en un componente interactivo. */
    record Custom(String trigger, Component component, String permission) {
    }

    private static final Format FALLBACK = new Format("",
            "<channel><prefix><tag><gray><player></gray> <dark_gray>›</dark_gray> <white><message>");

    private final SourceChat plugin;

    private volatile Set<String> modules = Set.of();
    private volatile List<Format> formats = List.of();
    private volatile List<Pattern> blocked = List.of();
    private volatile Map<String, Channel> channels = Map.of();
    private volatile String defaultChannel = "global";
    private volatile List<Custom> customs = List.of();
    private volatile Map<String, String> emojis = Map.of();
    private volatile boolean chatEnabled;
    private volatile boolean blockDupes;
    private volatile long cooldownMs;
    private volatile int capsMin;
    private volatile int capsPercent;
    private volatile boolean antiLink;
    private volatile Pattern linkPattern = Pattern.compile("a^");
    private volatile List<String> linkWhitelist = List.of();
    private volatile Pattern floodPattern;
    private volatile String floodReplacement = "";
    private volatile TextColor mentionColor = NamedTextColor.AQUA;
    private volatile String mentionSound = "block.note_block.bell";
    private volatile long snapshotMs;
    private volatile Set<String> discordChannels = Set.of();

    Settings(SourceChat plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        FileConfiguration c = plugin.getConfig();

        Set<String> mods = new HashSet<>();
        ConfigurationSection ms = c.getConfigurationSection("modules");
        if (ms != null) {
            for (String k : ms.getKeys(false)) if (ms.getBoolean(k)) mods.add(k.toLowerCase(Locale.ROOT));
        }
        modules = Set.copyOf(mods);

        chatEnabled = c.getBoolean("chat.enabled", true);
        cooldownMs = Math.max(0, c.getLong("chat.cooldown-ms", 1200));
        blockDupes = c.getBoolean("chat.block-duplicates", true);
        capsMin = c.getInt("chat.caps.min-letters", 8);
        capsPercent = c.getInt("chat.caps.percent", 70);

        List<Format> fs = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("chat.formats")) {
            Object perm = m.get("permission");
            Object fmt = m.get("format");
            if (fmt != null) fs.add(new Format(perm == null ? "" : String.valueOf(perm), String.valueOf(fmt)));
        }
        formats = List.copyOf(fs);

        List<Pattern> ps = new ArrayList<>();
        for (String s : c.getStringList("chat.blocked-words")) {
            Pattern pt = compile(s, "chat.blocked-words");
            if (pt != null) ps.add(pt);
        }
        blocked = List.copyOf(ps);

        antiLink = c.getBoolean("chat.anti-link.enabled", true);
        Pattern link = compile(c.getString("chat.anti-link.regex", "a^"), "chat.anti-link.regex");
        linkPattern = link != null ? link : Pattern.compile("a^");
        List<String> wl = new ArrayList<>();
        for (String w : c.getStringList("chat.anti-link.whitelist")) wl.add(w.toLowerCase(Locale.ROOT));
        linkWhitelist = List.copyOf(wl);

        int repeat = c.getInt("chat.anti-flood.max-repeat", 4);
        if (repeat > 0) {
            floodPattern = Pattern.compile("(.)\\1{" + repeat + ",}");
            floodReplacement = "$1".repeat(repeat);
        } else {
            floodPattern = null;
        }

        loadChannels(c);
        loadCustoms(c);

        Map<String, String> em = new LinkedHashMap<>();
        ConfigurationSection es = c.getConfigurationSection("emojis");
        if (es != null) {
            for (String key : es.getKeys(false)) {
                String val = es.getString(key);
                if (val != null && !key.isEmpty()) em.put(key, val);
            }
        }
        emojis = Map.copyOf(em);

        TextColor col = TextColor.fromHexString(c.getString("mentions.color", "#22D3EE"));
        mentionColor = col != null ? col : NamedTextColor.AQUA;
        mentionSound = c.getString("mentions.sound", "block.note_block.bell");

        snapshotMs = Math.max(1, c.getLong("tokens.snapshot-minutes", 10)) * 60_000L;
        discordChannels = Set.copyOf(c.getStringList("discord.channels"));
    }

    private Pattern compile(String regex, String where) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException ex) {
            plugin.getLogger().warning("Regex inválido en " + where + ": " + regex);
            return null;
        }
    }

    private void loadChannels(FileConfiguration c) {
        Map<String, Channel> map = new LinkedHashMap<>();
        ConfigurationSection cs = c.getConfigurationSection("channels.list");
        if (cs != null) {
            for (String id : cs.getKeys(false)) {
                ConfigurationSection x = cs.getConfigurationSection(id);
                if (x == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                map.put(key, new Channel(key,
                        x.getString("display", ""),
                        x.getString("permission", ""),
                        x.getInt("radius", -1),
                        x.getBoolean("world-only", false),
                        x.getString("shortcut", ""),
                        x.getString("format", "")));
            }
        }
        if (map.isEmpty()) map.put("global", new Channel("global", "", "", -1, false, "", ""));
        String def = c.getString("channels.default", "global").toLowerCase(Locale.ROOT);
        defaultChannel = map.containsKey(def) ? def : map.keySet().iterator().next();
        channels = Collections.unmodifiableMap(map);
    }

    private void loadCustoms(FileConfiguration c) {
        List<Custom> list = new ArrayList<>();
        ConfigurationSection ps = c.getConfigurationSection("custom-placeholders");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                ConfigurationSection x = ps.getConfigurationSection(key);
                if (x == null) continue;
                String trigger = x.getString("trigger", "");
                if (trigger.isEmpty()) continue;
                Component comp = Text.MM.deserialize(x.getString("text", trigger));
                String hover = x.getString("hover", "");
                if (!hover.isEmpty()) comp = comp.hoverEvent(HoverEvent.showText(Text.MM.deserialize(hover)));
                ClickEvent click = click(x.getString("click.action"), x.getString("click.value"));
                if (click != null) comp = comp.clickEvent(click);
                list.add(new Custom(trigger, comp, x.getString("permission", "")));
            }
        }
        customs = List.copyOf(list);
    }

    private ClickEvent click(String action, String value) {
        if (action == null || value == null || value.isEmpty()) return null;
        try {
            return switch (action.toLowerCase(Locale.ROOT)) {
                case "open_url" -> ClickEvent.openUrl(value);
                case "run_command" -> ClickEvent.runCommand(value);
                case "suggest_command" -> ClickEvent.suggestCommand(value);
                case "copy_to_clipboard", "copy" -> ClickEvent.copyToClipboard(value);
                default -> null;
            };
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Acción click inválida en custom-placeholders: " + action + " " + value);
            return null;
        }
    }

    Format formatFor(Player p) {
        for (Format f : formats) {
            if (f.permission().isEmpty() || p.hasPermission(f.permission())) return f;
        }
        return FALLBACK;
    }

    /** Canal por id; si no existe, el canal por defecto. */
    Channel channel(String id) {
        Map<String, Channel> m = channels;
        Channel ch = id == null ? null : m.get(id);
        if (ch == null) ch = m.get(defaultChannel);
        return ch != null ? ch : m.values().iterator().next();
    }

    // ------------------------------------------------------------ textos traducidos

    /** Texto crudo (MiniMessage) en el idioma del servidor. */
    String raw(String key) {
        return plugin.lang().get(plugin.lang().def(), key);
    }

    /** Texto crudo en el idioma de quien lo recibe. */
    String raw(CommandSender who, String key) {
        return plugin.lang().get(plugin.lang().of(who), key);
    }

    /** Mensaje sin prefijo. */
    Component fmt(String key, TagResolver... resolvers) {
        return Text.MM.deserialize(raw(key), TagResolver.resolver(resolvers));
    }

    Component fmt(CommandSender who, String key, TagResolver... resolvers) {
        return Text.MM.deserialize(raw(who, key), TagResolver.resolver(resolvers));
    }

    /** Texto de varias líneas (separadas por \n) como lista de componentes. */
    List<Component> lines(CommandSender who, String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        TagResolver r = TagResolver.resolver(resolvers);
        for (String line : raw(who, key).split("\n")) out.add(Text.MM.deserialize(line, r));
        return out;
    }

    /** Mensaje con prefijo (&lt;prefix&gt;). */
    Component msg(String key, TagResolver... resolvers) {
        return prefixed(raw(key), resolvers);
    }

    Component msg(CommandSender who, String key, TagResolver... resolvers) {
        return prefixed(raw(who, key), resolvers);
    }

    private Component prefixed(String raw, TagResolver... resolvers) {
        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = Placeholder.parsed("prefix", plugin.getConfig().getString("prefix", ""));
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return Text.MM.deserialize("<prefix>" + raw, TagResolver.resolver(all));
    }

    /** Mensaje de "estás silenciado" con tiempo restante y razón. */
    Component muted(CommandSender who, Profile pr) {
        String time = pr.muteUntil == Long.MAX_VALUE
                ? raw(who, "time-permanent")
                : Durations.format(pr.muteUntil - System.currentTimeMillis());
        return msg(who, "muted", Placeholder.parsed("time", time), Placeholder.unparsed("reason", pr.muteReason));
    }

    boolean module(String name) {
        return modules.contains(name);
    }

    boolean chatEnabled() { return chatEnabled; }
    boolean blockDupes() { return blockDupes; }
    long cooldownMs() { return cooldownMs; }
    int capsMin() { return capsMin; }
    int capsPercent() { return capsPercent; }
    List<Pattern> blocked() { return blocked; }
    boolean antiLink() { return antiLink; }
    Pattern linkPattern() { return linkPattern; }
    List<String> linkWhitelist() { return linkWhitelist; }
    Pattern floodPattern() { return floodPattern; }
    String floodReplacement() { return floodReplacement; }
    Map<String, Channel> channels() { return channels; }
    String defaultChannel() { return defaultChannel; }
    List<Custom> customs() { return customs; }
    Map<String, String> emojis() { return emojis; }
    TextColor mentionColor() { return mentionColor; }
    String mentionSound() { return mentionSound; }
    long snapshotMs() { return snapshotMs; }
    Set<String> discordChannels() { return discordChannels; }
}
