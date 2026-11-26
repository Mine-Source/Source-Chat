package dev.minesource.sourcechat;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minijuegos de chat: matemáticas, ordenar letras, escribir primero, trivia, al revés, letra faltante,
 * ítems de Minecraft, números romanos, binario, vocales, adivinar el número y ordenar números.
 * Recompensas: monedas y/o comandos. Ranking de victorias con /chatgame top.
 */
final class GameManager {

    static final List<String> KINDS = List.of("math", "unscramble", "type", "trivia", "reverse", "missing",
            "item", "roman", "binary", "vowels", "guess", "sort");

    private record Game(String kind, String question, String answer, long startedAt) {
    }

    private static final String CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final String VOWELS = "aeiouáéíóúü";

    private final SourceChat plugin;
    private BukkitTask timer;
    private BukkitTask timeout;
    private volatile Game current;
    private List<String> materialWords;

    GameManager(SourceChat plugin) {
        this.plugin = plugin;
    }

    void start() {
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

    boolean active() {
        return current != null;
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Lectura rápida desde el hilo del chat: ¿este mensaje puede ser un intento? */
    boolean interested(String text) {
        Game g = current;
        if (g == null) return false;
        String t = norm(text);
        return t.equals(g.answer()) || (g.kind().equals("guess") && t.matches("\\d{1,3}"));
    }

    /** Procesa un intento. Hilo principal. */
    void attempt(Player p, String text) {
        Game g = current;
        if (g == null) return;
        String t = norm(text);
        if (t.equals(g.answer())) {
            win(p, g);
        } else if (g.kind().equals("guess") && t.matches("\\d{1,3}")) {
            int n = Integer.parseInt(t);
            int target = Integer.parseInt(g.answer());
            p.sendMessage(plugin.settings().msg(p, n < target ? "game-higher" : "game-lower"));
        }
    }

    boolean startGame(boolean force) {
        return startGame(force, null);
    }

    /** Inicia un juego (kind null = aleatorio). Con force=true ignora el mínimo de jugadores. */
    boolean startGame(boolean force, String kind) {
        if (current != null) return false;
        var cfg = plugin.getConfig();
        if (!force && Bukkit.getOnlinePlayers().size() < cfg.getInt("games.min-players", 2)) return false;

        String chosen = kind;
        if (chosen == null) {
            List<String> types = new ArrayList<>();
            for (String t : cfg.getStringList("games.types")) if (KINDS.contains(t.toLowerCase(Locale.ROOT))) types.add(t.toLowerCase(Locale.ROOT));
            if (types.isEmpty()) types = KINDS;
            chosen = types.get(ThreadLocalRandom.current().nextInt(types.size()));
        }
        Game g = build(chosen);
        if (g == null) return false;
        current = g;

        int seconds = Math.max(5, cfg.getInt("games.answer-seconds", 30));
        Settings s = plugin.settings();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(s.fmt(p, "game-announce",
                    Placeholder.parsed("title", s.raw(p, "game-title-" + g.kind())),
                    Placeholder.unparsed("question", g.question()),
                    Placeholder.unparsed("seconds", String.valueOf(seconds))));
            if (plugin.profiles().of(p).sounds) {
                p.playSound(p.getLocation(), cfg.getString("games.sound", "entity.experience_orb.pickup"), 0.7f, 1.2f);
            }
        }
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

    private void win(Player p, Game g) {
        if (current != g) return;
        current = null;
        if (timeout != null) timeout.cancel();

        Profile pr = plugin.profiles().of(p);
        pr.wins++;
        plugin.profiles().touch();

        double secs = (System.currentTimeMillis() - g.startedAt()) / 1000.0;
        Bukkit.broadcast(plugin.settings().msg("game-win",
                Placeholder.unparsed("player", p.getName()),
                Placeholder.unparsed("answer", g.answer()),
                Placeholder.unparsed("time", String.format(Locale.ROOT, "%.2f", secs))));

        long coins = plugin.getConfig().getLong("games.rewards.coins", 0);
        if (coins > 0) {
            plugin.eco().deposit(p, coins);
            p.sendMessage(plugin.settings().msg(p, "game-reward", Placeholder.unparsed("coins", plugin.eco().fmt(coins))));
        }
        for (String cmd : plugin.getConfig().getStringList("games.rewards.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
    }

    // ------------------------------------------------------------ generación

    private static String scramble(String w) {
        if (w.length() < 3) return w;
        String out = w;
        for (int i = 0; i < 6 && out.equals(w); i++) {
            List<Character> chars = new ArrayList<>();
            for (char c : w.toCharArray()) chars.add(c);
            Collections.shuffle(chars);
            StringBuilder sb = new StringBuilder();
            chars.forEach(sb::append);
            out = sb.toString();
        }
        return out;
    }

    private static String roman(int n) {
        int[] v = {50, 40, 10, 9, 5, 4, 1};
        String[] r = {"L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                sb.append(r[i]);
                n -= v[i];
            }
        }
        return sb.toString();
    }

    private String word() {
        List<String> words = plugin.getConfig().getStringList("games.words");
        if (words.isEmpty()) return null;
        return words.get(ThreadLocalRandom.current().nextInt(words.size())).trim();
    }

    private String materialWord() {
        if (materialWords == null) {
            List<String> l = new ArrayList<>();
            for (Material m : Material.values()) {
                String n = m.name();
                if (m.isItem() && !n.startsWith("LEGACY") && !n.contains("_") && n.length() >= 5 && n.length() <= 10) {
                    l.add(n.toLowerCase(Locale.ROOT));
                }
            }
            materialWords = l;
        }
        return materialWords.isEmpty() ? null : materialWords.get(ThreadLocalRandom.current().nextInt(materialWords.size()));
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
                String w = word();
                return w == null ? null : new Game(kind, scramble(w), norm(w), now);
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
                return (q == null || a == null) ? null : new Game(kind, String.valueOf(q), norm(String.valueOf(a)), now);
            }
            case "reverse" -> {
                String w = word();
                return w == null ? null : new Game(kind, w, norm(new StringBuilder(w).reverse().toString()), now);
            }
            case "missing" -> {
                String w = word();
                if (w == null || w.length() < 4) return null;
                int i = r.nextInt(1, w.length());
                return new Game(kind, w.substring(0, i) + "_" + w.substring(i + 1), norm(w), now);
            }
            case "item" -> {
                String w = materialWord();
                return w == null ? null : new Game(kind, scramble(w), w, now);
            }
            case "roman" -> {
                int n = r.nextInt(1, 100);
                return new Game(kind, roman(n), String.valueOf(n), now);
            }
            case "binary" -> {
                int n = r.nextInt(2, 64);
                return new Game(kind, Integer.toBinaryString(n), String.valueOf(n), now);
            }
            case "vowels" -> {
                String w = word();
                if (w == null) return null;
                int count = 0;
                for (char c : w.toLowerCase(Locale.ROOT).toCharArray()) if (VOWELS.indexOf(c) >= 0) count++;
                return new Game(kind, w, String.valueOf(count), now);
            }
            case "guess" -> {
                return new Game(kind, "1 - 100", String.valueOf(r.nextInt(1, 101)), now);
            }
            case "sort" -> {
                List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9));
                Collections.shuffle(nums);
                List<Integer> five = new ArrayList<>(nums.subList(0, 5));
                StringBuilder q = new StringBuilder();
                five.forEach(n -> q.append(n).append(' '));
                Collections.sort(five);
                StringBuilder a = new StringBuilder();
                five.forEach(n -> a.append(n).append(' '));
                return new Game(kind, q.toString().trim(), a.toString().trim(), now);
            }
            default -> {
                return null;
            }
        }
    }

    // ------------------------------------------------------------ ranking y comandos

    void top(CommandSender s) {
        Settings st = plugin.settings();
        List<Profile> list = new ArrayList<>();
        for (Profile p : plugin.profiles().all()) if (p.wins > 0 && p.name != null) list.add(p);
        list.sort(Comparator.comparingInt((Profile p) -> p.wins).reversed());
        if (list.isEmpty()) {
            s.sendMessage(st.msg(s, "game-top-empty"));
            return;
        }
        s.sendMessage(st.fmt(s, "game-top-header"));
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            s.sendMessage(st.fmt(s, "game-top-line", Placeholder.unparsed("pos", String.valueOf(i + 1)),
                    Placeholder.unparsed("player", list.get(i).name), Placeholder.unparsed("wins", String.valueOf(list.get(i).wins))));
        }
    }

    /** /chatgame start [tipo] | stop | top | stats | types */
    void command(CommandSender s, String[] a) {
        Settings st = plugin.settings();
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top" -> top(s);
            case "stats" -> {
                if (s instanceof Player p) {
                    p.sendMessage(st.msg(p, "game-stats", Placeholder.unparsed("wins", String.valueOf(plugin.profiles().of(p).wins))));
                } else {
                    s.sendMessage(st.msg(s, "only-players"));
                }
            }
            case "types" -> s.sendMessage(st.msg(s, "game-types", Placeholder.unparsed("list", String.join(", ", KINDS))));
            case "start" -> {
                if (!s.hasPermission("sourcechat.admin")) {
                    s.sendMessage(st.msg(s, "no-permission"));
                    return;
                }
                String kind = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : null;
                if (kind != null && !KINDS.contains(kind)) {
                    s.sendMessage(st.msg(s, "game-types", Placeholder.unparsed("list", String.join(", ", KINDS))));
                    return;
                }
                s.sendMessage(st.msg(s, startGame(true, kind) ? "game-started-admin" : "game-already"));
            }
            case "stop" -> {
                if (!s.hasPermission("sourcechat.admin")) {
                    s.sendMessage(st.msg(s, "no-permission"));
                    return;
                }
                s.sendMessage(st.msg(s, stopGame() ? "game-stopped" : "game-none"));
            }
            default -> s.sendMessage(st.fmt(s, "game-usage"));
        }
    }

    List<String> tab(CommandSender s, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("top", "stats", "types"));
            if (s.hasPermission("sourcechat.admin")) out.addAll(List.of("start", "stop"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("start") && s.hasPermission("sourcechat.admin")) {
            out.addAll(KINDS);
        }
        return out;
    }
}
