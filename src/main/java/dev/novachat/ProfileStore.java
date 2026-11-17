package dev.novachat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Guarda perfiles en data.yml. Escritura asíncrona y agrupada (solo si hubo cambios). */
final class ProfileStore {

    private final NovaChat plugin;
    private final Path file;
    private final Map<UUID, Profile> map = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();

    ProfileStore(NovaChat plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
    }

    void load() {
        if (!Files.exists(file)) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection c = root.getConfigurationSection(key);
                if (c == null) continue;
                Profile p = new Profile();
                p.mentions = c.getBoolean("mentions", true);
                p.sounds = c.getBoolean("sounds", true);
                p.messages = c.getBoolean("messages", true);
                p.chat = c.getBoolean("chat", true);
                p.channel = c.getString("channel");
                p.muteUntil = c.getLong("mute-until", 0L);
                p.muteReason = c.getString("mute-reason", "");
                for (String u : c.getStringList("ignored")) {
                    try {
                        p.ignored.add(UUID.fromString(u));
                    } catch (IllegalArgumentException ignored) {
                        // entrada inválida, se omite
                    }
                }
                map.put(id, p);
            } catch (IllegalArgumentException ignored) {
                // clave inválida, se omite
            }
        }
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, 1200L, 1200L);
    }

    Profile get(UUID id) {
        return map.computeIfAbsent(id, k -> new Profile());
    }

    void touch() {
        dirty.set(true);
    }

    /** Serializa en el hilo principal y escribe en un hilo asíncrono. */
    void flush() {
        if (dirty.compareAndSet(true, false)) {
            String data = serialize();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(data));
        }
    }

    /** Guardado síncrono (apagado del servidor). */
    void saveNow() {
        if (dirty.getAndSet(false)) write(serialize());
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        map.forEach((id, p) -> {
            if (p.isDefault()) return;
            String base = "players." + id + ".";
            y.set(base + "mentions", p.mentions);
            y.set(base + "sounds", p.sounds);
            y.set(base + "messages", p.messages);
            y.set(base + "chat", p.chat);
            if (p.channel != null) y.set(base + "channel", p.channel);
            if (p.muted()) {
                y.set(base + "mute-until", p.muteUntil);
                y.set(base + "mute-reason", p.muteReason);
            }
            y.set(base + "ignored", p.ignored.stream().map(UUID::toString).toList());
        });
        return y.saveToString();
    }

    private void write(String data) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("data.yml.tmp");
            Files.writeString(tmp, data);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }
}
