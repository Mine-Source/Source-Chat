package dev.novachat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Corazón del plugin: canales, filtros, formato, tokens, menciones, ignorados, espía y Discord. */
final class ChatListener implements Listener {

    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_]{3,16})");

    private final NovaChat plugin;
    private final Map<UUID, Long> lastTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();

    ChatListener(NovaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Settings s = plugin.settings();
        if (!s.chatEnabled()) return;

        final Player p = e.getPlayer();
        final UUID id = p.getUniqueId();
        final Profile me = plugin.profiles().get(id);
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());

        // 1) Silencio, chat oculto y chat bloqueado.
        if (me.muted()) {
            e.setCancelled(true);
            p.sendMessage(s.muted(me));
            return;
        }
        if (!me.chat) {
            e.setCancelled(true);
            p.sendMessage(s.msg("chat-hidden-self"));
            return;
        }
        boolean bypassFilter = p.hasPermission("novachat.bypass.filter");
        if (plugin.state().locked && !p.hasPermission("novachat.bypass.lock")) {
            e.setCancelled(true);
            p.sendMessage(s.msg("chat-locked"));
            return;
        }

        // 2) Canal (atajo tipo "!" o canal activo del jugador).
        Channel picked = null;
        for (Channel c : s.channels().values()) {
            String sc = c.shortcut();
            if (!sc.isEmpty() && plain.length() > sc.length() && plain.startsWith(sc)
                    && !plain.startsWith(sc + sc) && c.canUse(p)) {
                picked = c;
                plain = plain.substring(sc.length()).stripLeading();
                break;
            }
        }
        if (picked == null) {
            picked = s.channel(me.channel);
            if (!picked.canUse(p)) picked = s.channel(null);
        }
        final Channel ch = picked;

        // 3) Filtros.
        if (!bypassFilter) {
            long now = System.currentTimeMillis();
            long wait = Math.max(s.cooldownMs(), plugin.state().slowSeconds * 1000L);
            Long last = lastTime.get(id);
            if (last != null && now - last < wait) {
                e.setCancelled(true);
                double left = (wait - (now - last)) / 1000.0;
                p.sendMessage(s.msg("cooldown", Placeholder.unparsed("seconds", String.format(Locale.ROOT, "%.1f", left))));
                return;
            }
            String prev = lastText.get(id);
            if (s.blockDupes() && prev != null && prev.equalsIgnoreCase(plain)) {
                e.setCancelled(true);
                p.sendMessage(s.msg("duplicate"));
                return;
            }
            for (Pattern pt : s.blocked()) {
                if (pt.matcher(plain).find()) {
                    e.setCancelled(true);
                    p.sendMessage(s.msg("blocked"));
                    return;
                }
            }
            if (!p.hasPermission("novachat.bypass.links") && hasBadLink(plain, s)) {
                e.setCancelled(true);
                p.sendMessage(s.msg("link-blocked"));
                return;
            }
            if (s.floodPattern() != null) {
                plain = s.floodPattern().matcher(plain).replaceAll(s.floodReplacement());
            }
            plain = fixCaps(plain, s);
        }
        if (plain.isBlank()) {
            e.setCancelled(true);
            return;
        }
        lastTime.put(id, System.currentTimeMillis());
        lastText.put(id, plain);

        // 4) Emojis (:smile: -> ☺).
        if (p.hasPermission("novachat.emoji")) {
            for (Map.Entry<String, String> en : s.emojis().entrySet()) {
                if (plain.contains(en.getKey())) plain = plain.replace(en.getKey(), en.getValue());
            }
        }
        final String text = plain;

        // 5) Minijuego activo: validar la respuesta en el hilo principal.
        if (plugin.games().isAnswer(text)) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.games().win(p, text));
        }

        // 6) Mensaje: estilos permitidos -> tokens -> placeholders propios -> menciones.
        Component msg = Text.user(p, text);
        msg = plugin.tokens().process(p, msg, text);
        for (Settings.Custom cu : s.customs()) {
            if (!cu.permission().isEmpty() && !p.hasPermission(cu.permission())) continue;
            msg = msg.replaceText(TextReplacementConfig.builder().matchLiteral(cu.trigger())
                    .replacement(cu.component()).build());
        }
        Set<Player> mentioned = ConcurrentHashMap.newKeySet();
        if (s.mentionsOn()) msg = highlightMentions(msg, mentioned, s);
        final Component body = msg;

        // 7) Formato (del canal o por permiso) + PlaceholderAPI.
        Settings.Format f = s.formatFor(p);
        String fmt = ch.format().isEmpty() ? f.format() : ch.format();
        if (plugin.papi()) fmt = PapiHook.apply(p, fmt);
        fmt = Text.legacyToMini(fmt);
        Component line = Text.MM.deserialize(fmt, TagResolver.resolver(
                Placeholder.parsed("player", p.getName()),
                Placeholder.component("displayname", p.displayName()),
                Placeholder.component("channel", ch.display().isEmpty() ? Component.empty() : Text.MM.deserialize(ch.display())),
                Placeholder.component("message", body)));
        e.message(body);
        e.renderer(ChatRenderer.viewerUnaware((source, name, message) -> line));

        // 8) Destinatarios: permiso del canal, radio/mundo, chat oculto e ignorados.
        final boolean bypassIgnore = p.hasPermission("novachat.bypass.ignore");
        final var origin = p.getLocation();
        e.viewers().removeIf(a -> {
            if (!(a instanceof Player v) || v.equals(p)) return false;
            if (!ch.canUse(v)) return true;
            if (ch.worldOnly() || ch.radius() >= 0) {
                if (!v.getWorld().equals(origin.getWorld())) return true;
                if (ch.radius() >= 0 && v.getLocation().distanceSquared(origin) > (double) ch.radius() * ch.radius()) {
                    return true;
                }
            }
            Profile vp = plugin.profiles().get(v.getUniqueId());
            return !vp.chat || (!bypassIgnore && vp.ignored.contains(id));
        });

        // 9) Espías (solo en canales restringidos), menciones y Discord.
        final Set<Audience> viewers = e.viewers();
        final boolean restricted = ch.radius() >= 0 || ch.worldOnly() || !ch.permission().isEmpty();
        final List<Player> spies = new ArrayList<>();
        if (restricted) {
            for (UUID u : plugin.state().spies) {
                Player sp = Bukkit.getPlayer(u);
                if (sp != null && !sp.equals(p) && !viewers.contains(sp)) spies.add(sp);
            }
        }
        mentioned.removeIf(t -> !viewers.contains(t));

        if (!mentioned.isEmpty() || !spies.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                mentioned.forEach(t -> notifyMention(p, t));
                if (!spies.isEmpty()) {
                    Component spy = s.fmt("spy-chat",
                            Placeholder.unparsed("channel", ch.id()),
                            Placeholder.unparsed("player", p.getName()),
                            Placeholder.unparsed("message", text));
                    spies.forEach(sp -> sp.sendMessage(spy));
                }
            });
        }
        if (s.discordEnabled() && s.discordChannels().contains(ch.id())) {
            plugin.discord().send(p.getName(), text);
        }
    }

    private boolean hasBadLink(String text, Settings s) {
        if (!s.antiLink()) return false;
        Matcher m = s.linkPattern().matcher(text);
        while (m.find()) {
            String host = m.group().toLowerCase(Locale.ROOT);
            int scheme = host.indexOf("://");
            if (scheme >= 0) host = host.substring(scheme + 3);
            int slash = host.indexOf('/');
            if (slash >= 0) host = host.substring(0, slash);
            if (host.startsWith("www.")) host = host.substring(4);
            boolean allowed = false;
            for (String w : s.linkWhitelist()) {
                if (host.equals(w) || host.endsWith("." + w)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) return true;
        }
        return false;
    }

    private Component highlightMentions(Component msg, Set<Player> out, Settings s) {
        return msg.replaceText(TextReplacementConfig.builder().match(MENTION).replacement((mr, b) -> {
            Player t = Bukkit.getPlayerExact(mr.group(1));
            if (t == null) return b;
            out.add(t);
            return b.content("@" + t.getName()).color(s.mentionColor()).decorate(TextDecoration.BOLD);
        }).build());
    }

    private void notifyMention(Player from, Player to) {
        if (from.equals(to)) return;
        Profile tp = plugin.profiles().get(to.getUniqueId());
        if (!tp.mentions) return;
        if (tp.ignored.contains(from.getUniqueId()) && !from.hasPermission("novachat.bypass.ignore")) return;
        Settings s = plugin.settings();
        to.sendActionBar(s.fmt("mention-actionbar", Placeholder.unparsed("player", from.getName())));
        if (tp.sounds) to.playSound(to.getLocation(), s.mentionSound(), 1f, 1.4f);
    }

    private static String fixCaps(String text, Settings s) {
        int letters = 0, upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        if (letters >= s.capsMin() && upper * 100 / letters >= s.capsPercent()) {
            return text.toLowerCase(Locale.ROOT);
        }
        return text;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastTime.remove(id);
        lastText.remove(id);
        plugin.state().spies.remove(id);
    }
}
