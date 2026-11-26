package dev.minesource.sourcechat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Estado global y temporal del chat (no se guarda en disco). */
final class ChatState {
    volatile boolean locked;
    volatile int slowSeconds;
    final Set<UUID> spies = ConcurrentHashMap.newKeySet();
}
