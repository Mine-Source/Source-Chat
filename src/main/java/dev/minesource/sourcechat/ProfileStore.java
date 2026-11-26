package dev.minesource.sourcechat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guarda perfiles en data.yml (escritura asíncrona, agrupada y solo si hubo cambios).
 * <p>
 * Identidad: en servidores premium se usa el UUID de Mojang; en servidores no premium (cracked) o
 * detrás de proxies con UUID inestables se usa un UUID derivado del nombre en minúsculas, así los
 * datos no se pierden aunque cambie el UUID. Configurable en identity.mode (auto | uuid | name).
 */
final class ProfileStore {

    /** Jugador buscado por nombre (online u offline). */
    record Target(UUID id, String name, Player online) {
    }

    private final SourceChat plugin;
    private final Path file;
    private final Map<UUID, Profile> map = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final boolean nameMode;

    ProfileStore(SourceChat plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.yml");
        String mode = plugin.getConfig().getString("identity.mode", "auto").toLowerCase(Locale.ROOT);
        this.nameMode = mode.equals("name") || (mode.equals("auto") && !Bukkit.getOnlineMode());
    }

    boolean nameMode() {
        return nameMode;
    }

    // ------------------------------------------------------------ identidad

    private static UUID derive(String name) {
        return UUID.nameUUIDFromBytes(("SourceChat:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    /** UUID con el que se guardan los datos de este jugador. */
    UUID id(OfflinePlayer p) {
        if (nameMode && p.getName() != null) return derive(p.getName());
        return p.getUniqueId();
    }

    Profile of(OfflinePlayer p) {
        return get(id(p));
    }

    Profile get(UUID id) {
        return map.computeIfAbsent(id, k -> new Profile());
    }

    /** Busca por nombre; en modo nombre no necesita que el jugador haya entrado antes. */
    Target target(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return new Target(id(on), on.getName(), on);
        if (nameMode) return new Target(derive(name), name, null);
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) return null;
        return new Target(op.getUniqueId(), op.getName() != null ? op.getName() : name, null);
    }

    Collection<Profile> all() {
        return map.values();
    }

    // ------------------------------------------------------------ persistencia

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
                p.lang = c.getString("lang");
                p.nick = c.getString("nick");
                p.coins = c.getLong("coins", 0L);
                p.wins = c.getInt("wins", 0);
                p.name = c.getString("name");
                p.owned.addAll(c.getStringList("owned"));
                ConfigurationSection eq = c.getConfigurationSection("equipped");
                if (eq != null) {
                    for (String t : eq.getKeys(false)) {
                        String v = eq.getString(t);
                        if (v != null) p.equipped.put(t, v);
                    }
                }
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
            String b = "players." + id + ".";
            if (p.name != null) y.set(b + "name", p.name);
            if (!p.mentions) y.set(b + "mentions", false);
            if (!p.sounds) y.set(b + "sounds", false);
            if (!p.messages) y.set(b + "messages", false);
            if (!p.chat) y.set(b + "chat", false);
            if (p.channel != null) y.set(b + "channel", p.channel);
            if (p.muted()) {
                y.set(b + "mute-until", p.muteUntil);
                y.set(b + "mute-reason", p.muteReason);
            }
            if (p.lang != null) y.set(b + "lang", p.lang);
            if (p.nick != null) y.set(b + "nick", p.nick);
            if (p.coins != 0) y.set(b + "coins", p.coins);
            if (p.wins != 0) y.set(b + "wins", p.wins);
            if (!p.owned.isEmpty()) y.set(b + "owned", p.owned.stream().sorted().toList());
            p.equipped.forEach((t, v) -> y.set(b + "equipped." + t, v));
            if (!p.ignored.isEmpty()) y.set(b + "ignored", p.ignored.stream().map(UUID::toString).toList());
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
