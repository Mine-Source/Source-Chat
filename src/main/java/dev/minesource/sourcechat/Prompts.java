package dev.minesource.sourcechat;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Captura el siguiente mensaje de chat de un jugador (para pedir datos desde un menú). */
final class Prompts {

    private final SourceChat plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    Prompts(SourceChat plugin) {
        this.plugin = plugin;
    }

    void ask(Player p, String messageKey, Consumer<String> handler, TagResolver... resolvers) {
        pending.put(p.getUniqueId(), handler);
        p.closeInventory();
        p.sendMessage(plugin.settings().msg(p, messageKey, resolvers));
        p.sendMessage(plugin.settings().msg(p, "prompt-cancel-hint"));
    }

    void clear(UUID id) {
        pending.remove(id);
    }

    /** Llamado desde el hilo del chat. true = el mensaje fue consumido. */
    boolean handle(Player p, String text) {
        Consumer<String> h = pending.remove(p.getUniqueId());
        if (h == null) return false;
        String t = text.trim();
        if (t.equalsIgnoreCase("cancel") || t.equalsIgnoreCase("cancelar")) {
            Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.settings().msg(p, "prompt-cancelled")));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> h.accept(t));
        }
        return true;
    }
}
