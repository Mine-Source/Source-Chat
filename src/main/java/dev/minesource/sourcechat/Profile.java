package dev.minesource.sourcechat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Preferencias, cosméticos y estado de un jugador. Thread-safe (campos volatile + colecciones concurrentes). */
final class Profile {
    volatile boolean mentions = true;
    volatile boolean sounds = true;
    volatile boolean messages = true;
    volatile boolean chat = true;
    volatile UUID lastMsg;
    /** Canal activo (null = el canal por defecto). */
    volatile String channel;
    /** Momento en que termina el silencio (0 = sin silencio, Long.MAX_VALUE = permanente). */
    volatile long muteUntil;
    volatile String muteReason = "";
    /** Idioma elegido (null = el del servidor). */
    volatile String lang;
    /** Apodo en formato MiniMessage restringido (null = sin apodo). */
    volatile String nick;
    volatile long coins;
    volatile int wins;
    /** Último nombre conocido (para rankings). */
    volatile String name;
    final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    /** Cosméticos comprados: "tipo:id". */
    final Set<String> owned = ConcurrentHashMap.newKeySet();
    /** Cosméticos equipados: tipo -> id. */
    final Map<String, String> equipped = new ConcurrentHashMap<>();
    /** Cosméticos probados por un admin sin comprarlos (no se guardan). */
    final Set<String> testing = ConcurrentHashMap.newKeySet();

    boolean muted() {
        return muteUntil > System.currentTimeMillis();
    }

    boolean isDefault() {
        return mentions && sounds && messages && chat && ignored.isEmpty() && channel == null && !muted()
                && lang == null && nick == null && coins == 0 && wins == 0 && owned.isEmpty() && equipped.isEmpty();
    }
}
