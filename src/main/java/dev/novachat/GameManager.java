package dev.novachat;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Minijuegos de chat: matemáticas, ordenar letras, escribir primero y trivia. */
final class GameManager {

    private record Game(String kind, String question, String answer, long startedAt) {
    }

    private static final String CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";

    private final NovaChat plugin;
    private BukkitTask timer;
    private BukkitTask timeout;
    private volatile Game current;

    GameManager(NovaChat plugin) {
        this.plugin = plugin;
    }

    void start() {
        if (!plugin.getConfig().getBoolean("games.enabled", true)) return;
        long ticks = Math.max(30, plugin.getConfig().getLong("games.interval-seconds", 300)) * 20L;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> startGame(false), ticks, ticks);
    }

    void stop() {
        if (timer != null) timer.cancel();
        if (timeout != null) timeout.cancel();
        timer = null;
        timeout = null;
        current = null;
    }

    void restart() {
        stop();
        start();
    }

    boolean active() {
        return current != null;
    }

    /** Lectura rápida desde el hilo del chat. */
    boolean isAnswer(String text) {
        Game g = current;
        return g != null && g.answer().equalsIgnoreCase(text.trim());
    }

    /** Inicia un juego. Con force=true ignora el mínimo de jugadores. Hilo principal. */
    boolean startGame(boolean force) {
        if (current != null) return false;
        var cfg = plugin.getConfig();
        if (!force && Bukkit.getOnlinePlayers().size() < cfg.getInt("games.min-players", 2)) return false;

        List<String> types = cfg.getStringList("games.types");
        if (types.isEmpty()) types = List.of("math", "unscramble", "type", "trivia");
        Game g = build(types.get(ThreadLocalRandom.current().nextInt(types.size())).toLowerCase(Locale.ROOT));
        if (g == null) return false;
        current = g;

        int seconds = Math.max(5, cfg.getInt("games.answer-seconds", 30));
        Settings s = plugin.settings();
        Bukkit.broadcast(s.fmt("game-announce",
                Placeholder.parsed("title", s.raw("game-title-" + g.kind())),
                Placeholder.unparsed("question", g.question()),
                Placeholder.unparsed("seconds", String.valueOf(seconds))));
        playAll(cfg.getString("games.sound", "entity.experience_orb.pickup"));

        timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (current == g) {
                current = null;
                Bukkit.broadcast(s.msg("game-expired", Placeholder.unparsed("answer", g.answer())));
            }
        }, seconds * 20L);
        return true;
    }

    boolean stopGame() {
        if (current == null) return false;
        current = null;
        if (timeout != null) timeout.cancel();
        return true;
    }

    /** Comprueba y premia la respuesta. Hilo principal. */
    void win(Player p, String text) {
        Game g = current;
        if (g == null || !g.answer().equalsIgnoreCase(text.trim())) return;
        current = null;
        if (timeout != null) timeout.cancel();

        double secs = (System.currentTimeMillis() - g.startedAt()) / 1000.0;
        Bukkit.broadcast(plugin.settings().msg("game-win",
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("answer", g.answer()),
                Placeholder.unparsed("time", String.format(Locale.ROOT, "%.2f", secs))));

        for (String cmd : plugin.getConfig().getStringList("games.rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
    }

    private Game build(String kind) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        switch (kind) {
            case "math" -> {
                int a = r.nextInt(2, 30), b = r.nextInt(2, 12);
                return switch (r.nextInt(3)) {
                    case 0 -> new Game(kind, a + " + " + b + " = ?", String.valueOf(a + b), now);
                    case 1 -> new Game(kind, (a + b) + " - " + b + " = ?", String.valueOf(a), now);
                    default -> new Game(kind, a + " x " + b + " = ?", String.valueOf(a * b), now);
                };
            }
            case "unscramble" -> {
                List<String> words = plugin.getConfig().getStringList("games.words");
                if (words.isEmpty()) return null;
                String w = words.get(r.nextInt(words.size()));
                String scrambled = w;
                for (int i = 0; i < 5 && scrambled.equals(w) && w.length() > 2; i++) {
                    List<Character> chars = new ArrayList<>();
                    for (char c : w.toCharArray()) chars.add(c);
                    Collections.shuffle(chars);
                    StringBuilder sb = new StringBuilder();
                    chars.forEach(sb::append);
                    scrambled = sb.toString();
                }
                return new Game(kind, scrambled, w, now);
            }
            case "type" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
                return new Game(kind, sb.toString(), sb.toString(), now);
            }
            case "trivia" -> {
                List<Map<?, ?>> list = plugin.getConfig().getMapList("games.trivia");
                if (list.isEmpty()) return null;
                Map<?, ?> m = list.get(r.nextInt(list.size()));
                Object q = m.get("q"), a = m.get("a");
                return (q == null || a == null) ? null : new Game(kind, String.valueOf(q), String.valueOf(a), now);
            }
            default -> {
                return null;
            }
        }
    }

    private void playAll(String sound) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.profiles().get(p.getUniqueId()).sounds) p.playSound(p.getLocation(), sound, 0.7f, 1.2f);
        }
    }
}
