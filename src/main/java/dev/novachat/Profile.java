package dev.novachat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Preferencias y estado de un jugador. Thread-safe (campos volatile + set concurrente). */
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
    final Set<UUID> ignored = ConcurrentHashMap.newKeySet();

    boolean muted() {
        return muteUntil > System.currentTimeMillis();
    }

    boolean isDefault() {
        return mentions && sounds && messages && chat && ignored.isEmpty() && channel == null && !muted();
    }
}
