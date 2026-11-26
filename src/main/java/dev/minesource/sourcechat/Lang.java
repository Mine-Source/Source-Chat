package dev.minesource.sourcechat;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Idiomas. Cada archivo plugins/SourceChat/lang/&lt;codigo&gt;.yml es un idioma (se pueden añadir más).
 * Las claves que falten en un archivo se completan con la copia interna del .jar, así las
 * actualizaciones nunca dejan textos vacíos ni pisan lo que el usuario editó.
 */
final class Lang {

    record Meta(String code, String name, String flag, String icon) {
    }

    private static final String[] BUNDLED = {"es", "en"};

    private final SourceChat plugin;
    private volatile Map<String, Map<String, String>> files = Map.of();
    private volatile Map<String, Map<String, String>> bundled = Map.of();
    private volatile String def = "es";

    Lang(SourceChat plugin) {
        this.plugin = plugin;
    }

    void load() {
        File dir = new File(plugin.getDataFolder(), "lang");
        dir.mkdirs();

        Map<String, Map<String, String>> jar = new HashMap<>();
        for (String code : BUNDLED) {
            if (!new File(dir, code + ".yml").exists()) plugin.saveResource("lang/" + code + ".yml", false);
            try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                if (in != null) {
                    jar.put(code, flatten(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))));
                }
            } catch (IOException ignored) {
                // sin copia interna: solo se usa el archivo del usuario
            }
        }

        Map<String, Map<String, String>> user = new TreeMap<>();
        File[] list = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (list != null) {
            for (File f : list) {
                String code = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                user.put(code, flatten(YamlConfiguration.loadConfiguration(f)));
            }
        }
        bundled = jar;
        files = user;

        String d = plugin.getConfig().getString("language.default", "es").toLowerCase(Locale.ROOT);
        if (user.containsKey(d)) def = d;
        else if (user.containsKey("en")) def = "en";
        else def = user.isEmpty() ? "es" : user.keySet().iterator().next();
    }

    private static Map<String, String> flatten(YamlConfiguration y) {
        Map<String, String> m = new HashMap<>();
        for (String k : y.getKeys(true)) {
            if (y.isConfigurationSection(k)) continue;
            Object o = y.get(k);
            if (o instanceof List<?> l) {
                StringJoiner j = new StringJoiner("\n");
                for (Object x : l) j.add(String.valueOf(x));
                m.put(k, j.toString());
            } else if (o != null) {
                m.put(k, String.valueOf(o));
            }
        }
        return m;
    }

    String def() {
        return def;
    }

    boolean has(String code) {
        return code != null && files.containsKey(code.toLowerCase(Locale.ROOT));
    }

    /** Códigos de idioma disponibles; el predeterminado primero. */
    List<String> codes() {
        List<String> out = new ArrayList<>(files.keySet());
        out.remove(def);
        out.add(0, def);
        return out;
    }

    Meta meta(String code) {
        String name = look(code, "meta-name");
        return new Meta(code, name != null ? name : code, look(code, "meta-flag"), look(code, "meta-icon"));
    }

    /** Idioma de quien recibe el mensaje (consola = predeterminado). */
    String of(CommandSender who) {
        if (who instanceof Player p && plugin.getConfig().getBoolean("language.per-player", true)) {
            String l = plugin.profiles().of(p).lang;
            if (l != null && files.containsKey(l)) return l;
        }
        return def;
    }

    String get(String code, String key) {
        String v = look(code, key);
        if (v != null) return v;
        if (!code.equals(def)) {
            v = look(def, key);
            if (v != null) return v;
        }
        v = look("en", key);
        return v != null ? v : "<red>[" + key + "]";
    }

    private String look(String code, String key) {
        Map<String, String> m = files.get(code);
        String v = m == null ? null : m.get(key);
        if (v != null) return v;
        m = bundled.get(code);
        return m == null ? null : m.get(key);
    }
}
