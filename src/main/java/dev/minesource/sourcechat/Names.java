package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Nombres y apodos. Calcula cómo se ve un jugador (apodo + color de nombre) y lo sincroniza con el
 * nombre visible (displayName) y la lista de jugadores (tab), para que cualquier plugin de tab o scoreboard
 * lo vea igual. También se expone por PlaceholderAPI (%sourcechat_displayname%, %sourcechat_prefix%...).
 */
final class Names {

    static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final SourceChat plugin;
    private boolean applied;

    Names(SourceChat plugin) {
        this.plugin = plugin;
    }

    boolean enabled() {
        return plugin.settings().module("namesync");
    }

    private static String plainOf(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(Text.SAFE.deserialize(mini));
    }

    /** Texto visible del nombre (apodo sin colores, o el nombre real). */
    String plainName(Player p, Profile pr) {
        return pr.nick != null ? plainOf(pr.nick) : p.getName();
    }

    /** Componente del nombre: apodo y color de nombre equipado. Seguro desde cualquier hilo. */
    Component name(Player p, Profile pr) {
        Cosmetics c = plugin.cosmetics();
        String style = c == null ? null : c.nameStyle(pr);
        if (style != null) return Text.MM.deserialize(style + Text.MM.escapeTags(plainName(p, pr)));
        if (pr.nick != null) return Text.SAFE.deserialize(pr.nick);
        return Component.text(p.getName());
    }

    Component tab(Player p, Profile pr) {
        Cosmetics c = plugin.cosmetics();
        String fmt = plugin.getConfig().getString("namesync.tab-format", "<prefix><name>");
        if (plugin.papi()) fmt = PapiHook.apply(p, fmt);
        fmt = Text.legacyToMini(fmt);
        return Text.MM.deserialize(fmt, TagResolver.resolver(
                Placeholder.component("prefix", c == null ? Component.empty() : c.slot(pr, CType.PREFIX)),
                Placeholder.component("tag", c == null ? Component.empty() : c.slot(pr, CType.TAG)),
                Placeholder.component("name", name(p, pr)),
                Placeholder.parsed("player", p.getName())));
    }

    /** Texto legacy (&amp;#rrggbb) para plugins de tab externos. */
    String legacy(Component c) {
        return LEGACY.serialize(c);
    }

    // ------------------------------------------------------------ aplicar al servidor (hilo principal)

    void apply(Player p) {
        if (!enabled()) return;
        applied = true;
        Profile pr = plugin.profiles().of(p);
        try {
            p.displayName(name(p, pr));
            FileConfiguration cfg = plugin.getConfig();
            if (cfg.getBoolean("namesync.tab", true)) p.playerListName(tab(p, pr));
            if (cfg.getBoolean("namesync.nametag", false)) team(p, pr);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("namesync: " + ex.getMessage());
        }
    }

    void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    void reset(Player p) {
        p.displayName(null);
        p.playerListName(null);
        removeTeam(p);
    }

    void resetAll() {
        if (!applied) return;
        applied = false;
        for (Player p : Bukkit.getOnlinePlayers()) reset(p);
    }

    private static String teamId(Player p) {
        return "nc" + Integer.toHexString(p.getName().hashCode());
    }

    private void team(Player p, Profile pr) {
        var mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard sb = mgr.getMainScoreboard();
        Team t = sb.getTeam(teamId(p));
        if (t == null) t = sb.registerNewTeam(teamId(p));
        Cosmetics c = plugin.cosmetics();
        t.prefix(c == null ? Component.empty() : c.slot(pr, CType.PREFIX));
        Component tag = c == null ? null : c.component(pr, CType.TAG);
        t.suffix(tag == null ? Component.empty() : Component.space().append(tag));
        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());
    }

    void removeTeam(Player p) {
        var mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Team t = mgr.getMainScoreboard().getTeam(teamId(p));
        if (t != null) t.unregister();
    }

    // ------------------------------------------------------------ apodos

    /**
     * Valida y guarda un apodo. Envía el resultado a quien lo pidió.
     * Límites configurables en nick.*: longitud visible, longitud con etiquetas, caracteres, nombres bloqueados.
     */
    void setNick(CommandSender setter, Player target, String input) {
        Settings st = plugin.settings();
        FileConfiguration c = plugin.getConfig();
        boolean colors = setter.hasPermission("sourcechat.nick.color");
        String stored = colors ? Text.legacyToMini(input.trim()) : Text.MM.escapeTags(input.trim());
        String visible = plainOf(stored).trim();

        int min = Math.max(1, c.getInt("nick.min-length", 3));
        int max = Math.max(min, c.getInt("nick.max-length", 16));
        if (visible.length() < min) {
            setter.sendMessage(st.msg(setter, "nick-too-short", Placeholder.unparsed("min", String.valueOf(min))));
            return;
        }
        if (visible.length() > max || stored.length() > c.getInt("nick.max-raw-length", 140)) {
            setter.sendMessage(st.msg(setter, "nick-too-long", Placeholder.unparsed("max", String.valueOf(max))));
            return;
        }
        String allowed = c.getString("nick.allowed-regex", "^[\\p{L}\\p{N}_ ]+$");
        try {
            if (!Pattern.compile(allowed).matcher(visible).matches()) {
                setter.sendMessage(st.msg(setter, "nick-invalid-chars"));
                return;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("nick.allowed-regex inválido: " + allowed);
        }
        String lower = visible.toLowerCase(Locale.ROOT);
        if (!setter.hasPermission("sourcechat.nick.bypass")) {
            for (String b : c.getStringList("nick.blocked")) {
                try {
                    if (Pattern.compile(b, Pattern.CASE_INSENSITIVE).matcher(visible).find()) {
                        setter.sendMessage(st.msg(setter, "nick-blocked"));
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // regex inválido: se omite
                }
            }
        }
        if (c.getBoolean("nick.unique", true) && taken(lower, target)) {
            setter.sendMessage(st.msg(setter, "nick-taken"));
            return;
        }
        Profile pr = plugin.profiles().of(target);
        pr.nick = stored;
        plugin.profiles().touch();
        apply(target);
        if (setter.equals(target)) {
            setter.sendMessage(st.msg(setter, "nick-set", Placeholder.component("nick", name(target, pr))));
        } else {
            setter.sendMessage(st.msg(setter, "nick-set-other", Placeholder.unparsed("player", target.getName()),
                    Placeholder.component("nick", name(target, pr))));
            target.sendMessage(st.msg(target, "nick-set", Placeholder.component("nick", name(target, pr))));
        }
    }

    void resetNick(CommandSender setter, Player target) {
        Profile pr = plugin.profiles().of(target);
        pr.nick = null;
        plugin.profiles().touch();
        if (enabled()) apply(target);
        else reset(target);
        Settings st = plugin.settings();
        setter.sendMessage(st.msg(setter, setter.equals(target) ? "nick-reset" : "nick-reset-other",
                Placeholder.unparsed("player", target.getName())));
        if (!setter.equals(target)) target.sendMessage(st.msg(target, "nick-reset", Placeholder.unparsed("player", target.getName())));
    }

    private boolean taken(String lowerVisible, Player self) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(self) && o.getName().equalsIgnoreCase(lowerVisible)) return true;
        }
        Profile mine = plugin.profiles().of(self);
        for (Profile pr : plugin.profiles().all()) {
            if (pr != mine && pr.nick != null && plainOf(pr.nick).trim().equalsIgnoreCase(lowerVisible)) return true;
        }
        return false;
    }

    /** Busca un jugador conectado por su apodo (visible, sin colores) o nombre real. */
    Player byName(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<Player, Profile> e : online().entrySet()) {
            if (e.getKey().getName().equalsIgnoreCase(t)) return e.getKey();
            if (e.getValue().nick != null && plainOf(e.getValue().nick).trim().equalsIgnoreCase(t)) return e.getKey();
        }
        return null;
    }

    private Map<Player, Profile> online() {
        Map<Player, Profile> m = new java.util.LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) m.put(p, plugin.profiles().of(p));
        return m;
    }
}
