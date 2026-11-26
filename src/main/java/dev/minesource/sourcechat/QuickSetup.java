package dev.minesource.sourcechat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configuración rápida: una lista de nombres (en el chat, por comando o en un archivo) se convierte
 * automáticamente en cosméticos con colores, degradados, adornos y precios. Los archivos procesados se
 * mueven a quick/procesado/ para no repetirse.
 * <p>
 * Línea: <code>Nombre | precio | colores | rareza</code> (todo opcional y en cualquier orden después del nombre).
 * Colores: <code>#ff0000</code>, <code>red</code>, <code>red &gt; gold</code> (degradado) o <code>rainbow</code>.
 */
final class QuickSetup {

    private final SourceChat plugin;
    private BukkitTask task;

    QuickSetup(SourceChat plugin) {
        this.plugin = plugin;
    }

    void start() {
        File dir = new File(plugin.getDataFolder(), "quick");
        dir.mkdirs();
        writeReadme(new File(dir, "LEEME-README.txt"));
        scan();
        long every = plugin.getConfig().getLong("quick.auto-scan-seconds", 30);
        if (every > 0) task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, every * 20L, every * 20L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    // ------------------------------------------------------------ generación

    /** Convierte líneas en cosméticos del tipo dado (no guarda nada). */
    static List<Cosmetics.Item> build(SourceChat plugin, CType type, List<String> lines, Set<String> taken) {
        var cfg = plugin.getConfig();
        long base = Math.max(0, cfg.getLong("quick.base-price", 250));
        boolean caps = cfg.getBoolean("quick.small-caps", false);
        List<Cosmetics.Item> out = new ArrayList<>();
        Set<String> used = new HashSet<>(taken);
        int i = 0;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("# ")) continue;
            String[] f = line.split("\\s*\\|\\s*");
            String text = f[0].trim();
            if (text.isEmpty()) continue;

            Long price = null;
            String colors = null;
            String rarity = null;
            for (int k = 1; k < f.length; k++) {
                String x = f[k].trim();
                if (x.isEmpty()) continue;
                if (x.matches("\\d{1,9}")) price = Long.parseLong(x);
                else if (Styler.isRarity(x)) rarity = x.toLowerCase(Locale.ROOT);
                else colors = x;
            }

            int h = Math.floorMod(text.hashCode(), 100000);
            int seed = h + i * 7;
            i++;
            if (rarity == null) rarity = Styler.autoRarity(h);
            long cost = price != null ? price : base * Styler.multiplier(rarity);
            boolean bold = rarity.equals("epic") || rarity.equals("legendary");
            Styler.Spec sp = Styler.spec(colors, seed);

            String name = text;
            String value;
            String sub = "";
            Material icon = type.icon;
            switch (type) {
                case PREFIX, TAG -> {
                    String label = caps ? Styler.smallCaps(text) : text;
                    String[] br = Styler.brackets(seed);
                    value = Styler.wrap(sp, br[0] + Text.MM.escapeTags(label) + br[1], bold);
                }
                case NAMECOLOR -> {
                    value = sp.open() + (bold ? "<bold>" : "");
                    icon = Styler.wool(sp.hex());
                }
                case CHATCOLOR -> {
                    value = sp.open();
                    icon = Styler.dye(sp.hex());
                }
                case JOINMSG, QUITMSG -> {
                    name = Styler.plain(text, 28);
                    value = Styler.wrap(sp, text.contains("<player>") ? text : text + " <player>", false);
                }
                default -> { // JOINTITLE
                    String[] ts = text.split("\\s*;\\s*", 2);
                    name = Styler.plain(ts[0], 28);
                    value = Styler.wrap(sp, ts[0], true);
                    String s = ts.length > 1 ? ts[1] : "";
                    sub = s.isEmpty() ? "<gray><player>" : (s.contains("<player>") ? s : "<gray>" + s);
                }
            }
            if (name.isEmpty()) name = "item";

            String id = Styler.slug(name);
            String unique = id;
            for (int n = 2; used.contains(unique); n++) unique = id + "-" + n;
            used.add(unique);
            out.add(new Cosmetics.Item(type, unique, name, value, sub, cost, icon, rarity, "", true));
        }
        return out;
    }

    /** Separa una lista escrita en el chat: saltos de línea, o comas (o // en mensajes). */
    static List<String> split(CType type, String input) {
        String sep = input.contains("\n") ? "\\R" : (type.message ? "//" : ",");
        List<String> out = new ArrayList<>();
        for (String s : input.split(sep)) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    /** Genera y guarda. Devuelve cuántos cosméticos se crearon, o -1 si el módulo está desactivado. */
    int apply(CType type, List<String> lines) {
        Cosmetics c = plugin.cosmetics();
        if (c == null) return -1;
        List<Cosmetics.Item> built = build(plugin, type, lines, c.ids(type));
        if (!built.isEmpty()) c.addAll(type, built);
        return built.size();
    }

    // ------------------------------------------------------------ archivos

    /** Procesa quick/*.txt (el nombre del archivo indica el tipo) y quick/*.yml (claves = tipos). */
    Map<CType, Integer> scan() {
        Map<CType, Integer> result = new EnumMap<>(CType.class);
        File dir = new File(plugin.getDataFolder(), "quick");
        File[] files = dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".txt") || l.endsWith(".yml");
        });
        if (files == null || plugin.cosmetics() == null) return result;

        for (File f : files) {
            String name = f.getName();
            if (name.toUpperCase(Locale.ROOT).startsWith("LEEME")) continue;
            boolean yml = name.toLowerCase(Locale.ROOT).endsWith(".yml");
            String base = name.substring(0, name.lastIndexOf('.'));
            boolean any = false;
            try {
                if (yml) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                    for (String key : y.getKeys(false)) {
                        CType t = CType.parse(key);
                        if (t == null) continue;
                        int n = apply(t, y.getStringList(key));
                        if (n >= 0) {
                            result.merge(t, n, Integer::sum);
                            any = true;
                        }
                    }
                } else {
                    CType t = CType.parse(base.split("[-_ .]")[0]);
                    if (t != null) {
                        int n = apply(t, Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
                        if (n >= 0) {
                            result.merge(t, n, Integer::sum);
                            any = true;
                        }
                    }
                }
                if (any) archive(f, base, yml ? ".yml" : ".txt");
            } catch (IOException ex) {
                plugin.getLogger().warning("No se pudo procesar quick/" + name + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private void archive(File f, String base, String ext) throws IOException {
        Path done = f.getParentFile().toPath().resolve("procesado");
        Files.createDirectories(done);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Files.move(f.toPath(), done.resolve(base + "-" + stamp + ext), StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeReadme(File f) {
        if (f.exists()) return;
        String text = """
                SOURCE CHAT · CONFIGURACIÓN RÁPIDA / QUICK SETUP
                ==============================================
                ES: Crea aquí un archivo .txt cuyo nombre sea el tipo (tags.txt, prefixes.txt, namecolor.txt,
                    chatcolor.txt, joinmsg.txt, quitmsg.txt, jointitle.txt) con UN nombre por línea.
                    SourceChat lo convierte solo en cosméticos con colores, degradados, adornos y precios,
                    y mueve el archivo a la carpeta "procesado".
                EN: Drop a .txt file here named after the type (see above) with ONE name per line.
                    SourceChat turns it into cosmetics automatically and archives the file in "procesado".

                Formato de línea / Line format (todo opcional / all optional):
                    Nombre | precio | colores | rareza
                    Rey
                    Dragón | 800 | red > gold | epic
                    Fantasma | 300 | #aabbcc
                    Arcoíris | 1500 | rainbow | legendary
                Mensajes / messages:  usa <player> para el nombre  ->  Bienvenido <player>
                Títulos / titles:     Título ; Subtítulo           ->  Hola ; <player>

                También puedes usar un .yml con listas / You can also use a .yml with lists:
                    tags: [VIP, MVP, Rey]
                    chatcolor: ["Fuego|500|red>gold"]

                En el juego / In game:  /sourcechat quick tag Rey, Reina, VIP   (o desde el panel admin / or the admin panel)
                Colores: nombres en español o inglés, #hex, "a > b" (degradado) o "rainbow".
                """;
        try {
            Files.writeString(f.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // no es crítico
        }
    }
}
