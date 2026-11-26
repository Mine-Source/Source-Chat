package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Entrada y salida: mensajes de bienvenida (cosméticos), títulos, mensaje de bienvenida y sincronización de nombres. */
final class JoinListener implements Listener {

    private final SourceChat plugin;

    JoinListener(SourceChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        final Profile pr = plugin.profiles().of(p);
        pr.name = p.getName();
        Settings s = plugin.settings();

        Cosmetics cos = plugin.cosmetics();
        Component custom = null;
        if (cos != null) {
            try {
                cos.sanitize(p, pr);
                custom = cos.joinMessage(p, pr);
            } catch (RuntimeException ex) {
                plugin.failModule("cosmetics", ex);
            }
        }
        if (s.module("joinquit")) {
            e.joinMessage(custom != null ? custom : s.fmt("join",
                    Placeholder.unparsed("player", p.getName()),
                    Placeholder.component("displayname", plugin.names().name(p, pr))));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.names().apply(p);
            Cosmetics c = plugin.cosmetics();
            if (c != null) {
                try {
                    c.joinTitle(p, pr);
                } catch (RuntimeException ex) {
                    plugin.failModule("cosmetics", ex);
                }
            }
            if (plugin.getConfig().getBoolean("welcome.enabled", true)) {
                p.sendMessage(s.fmt(p, "welcome-message", Placeholder.unparsed("player", p.getName())));
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().of(p);
        pr.testing.clear();
        Settings s = plugin.settings();
        if (s.module("joinquit")) {
            Component custom = null;
            Cosmetics cos = plugin.cosmetics();
            if (cos != null) {
                try {
                    custom = cos.quitMessage(p, pr);
                } catch (RuntimeException ex) {
                    plugin.failModule("cosmetics", ex);
                }
            }
            e.quitMessage(custom != null ? custom : s.fmt("quit",
                    Placeholder.unparsed("player", p.getName()),
                    Placeholder.component("displayname", plugin.names().name(p, pr))));
        }
        plugin.names().removeTeam(p);
    }
}
