package dev.minesource.sourcechat;

import org.bukkit.Material;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generador de estilos: colores, degradados, decoraciones y símbolos para la configuración rápida. */
final class Styler {

    /** Estilo MiniMessage: etiqueta de apertura, de cierre y primer color (#rrggbb). */
    record Spec(String open, String close, String hex) {
    }

    private static final String[] PAIRS = {
            "#f43f5e:#f59e0b", "#8b5cf6:#22d3ee", "#22c55e:#06b6d4", "#f97316:#eab308",
            "#ec4899:#8b5cf6", "#3b82f6:#06b6d4", "#ef4444:#f97316", "#10b981:#84cc16",
            "#a855f7:#ec4899", "#0ea5e9:#6366f1", "#facc15:#f97316", "#14b8a6:#3b82f6",
            "#e11d48:#7c3aed", "#84cc16:#22c55e", "#f472b6:#fb7185", "#38bdf8:#818cf8"
    };

    private static final String[][] BRACKETS = {
            {"[", "]"}, {"❰", "❱"}, {"«", "»"}, {"⟦", "⟧"}, {"⌈", "⌉"}, {"(", ")"}, {"✦ ", " ✦"}
    };

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000aa"), Map.entry("dark_green", "#00aa00"),
            Map.entry("dark_aqua", "#00aaaa"), Map.entry("dark_red", "#aa0000"), Map.entry("dark_purple", "#aa00aa"),
            Map.entry("gold", "#ffaa00"), Map.entry("gray", "#aaaaaa"), Map.entry("dark_gray", "#555555"),
            Map.entry("blue", "#5555ff"), Map.entry("green", "#55ff55"), Map.entry("aqua", "#55ffff"),
            Map.entry("red", "#ff5555"), Map.entry("light_purple", "#ff55ff"), Map.entry("yellow", "#ffff55"),
            Map.entry("white", "#ffffff"),
            Map.entry("negro", "#000000"), Map.entry("azul", "#5555ff"), Map.entry("azul_oscuro", "#0000aa"),
            Map.entry("verde", "#55ff55"), Map.entry("verde_oscuro", "#00aa00"), Map.entry("celeste", "#55ffff"),
            Map.entry("cian", "#00aaaa"), Map.entry("rojo", "#ff5555"), Map.entry("rojo_oscuro", "#aa0000"),
            Map.entry("morado", "#aa00aa"), Map.entry("rosa", "#ff55ff"), Map.entry("dorado", "#ffaa00"),
            Map.entry("naranja", "#ff8800"), Map.entry("amarillo", "#ffff55"), Map.entry("gris", "#aaaaaa"),
            Map.entry("gris_oscuro", "#555555"), Map.entry("blanco", "#ffffff"),
            Map.entry("orange", "#ff8800"), Map.entry("pink", "#ff55ff"), Map.entry("purple", "#aa00aa"),
            Map.entry("cyan", "#00aaaa"), Map.entry("lime", "#84cc16"), Map.entry("brown", "#835432"));

    private static final String[] DYE = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
    private static final int[] DYE_RGB = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";

    private Styler() {
    }

    // ------------------------------------------------------------ colores

    /** Convierte "#rrggbb", "rrggbb" o un nombre (en/es) a "#rrggbb"; null si no es un color. */
    static String hex(String s) {
        if (s == null) return null;
        String c = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (c.matches("#[0-9a-f]{6}")) return c;
        if (c.matches("[0-9a-f]{6}")) return "#" + c;
        return NAMED.get(c);
    }

    private static Spec gradient(String pair) {
        String[] p = pair.split(":");
        return new Spec("<gradient:" + p[0] + ":" + p[1] + ">", "</gradient>", p[0]);
    }

    /** Interpreta "a > b", "#hex", "rainbow"; si está vacío elige una combinación según la semilla. */
    static Spec spec(String colors, int seed) {
        if (colors == null || colors.isBlank()) return gradient(PAIRS[Math.floorMod(seed, PAIRS.length)]);
        String c = colors.trim().toLowerCase(Locale.ROOT);
        if (c.equals("rainbow") || c.equals("arcoiris") || c.equals("arcoíris")) {
            return new Spec("<rainbow>", "</rainbow>", "#ff5555");
        }
        List<String> hexes = new ArrayList<>();
        for (String part : c.split("\\s*>\\s*")) {
            String h = hex(part);
            if (h != null) hexes.add(h);
        }
        if (hexes.isEmpty()) return spec(null, seed);
        if (hexes.size() == 1) return new Spec("<" + hexes.get(0) + ">", "</" + hexes.get(0) + ">", hexes.get(0));
        return new Spec("<gradient:" + String.join(":", hexes) + ">", "</gradient>", hexes.get(0));
    }

    static String wrap(Spec sp, String text, boolean bold) {
        return sp.open() + (bold ? "<bold>" : "") + text + (bold ? "</bold>" : "") + sp.close();
    }

    // ------------------------------------------------------------ rareza y precio

    static boolean isRarity(String s) {
        String k = s.toLowerCase(Locale.ROOT);
        return k.equals("common") || k.equals("rare") || k.equals("epic") || k.equals("legendary");
    }

    static String autoRarity(int h) {
        int r = Math.floorMod(h, 100);
        if (r < 50) return "common";
        if (r < 80) return "rare";
        if (r < 95) return "epic";
        return "legendary";
    }

    static long multiplier(String rarity) {
        return switch (rarity) {
            case "rare" -> 2;
            case "epic" -> 4;
            case "legendary" -> 8;
            default -> 1;
        };
    }

    // ------------------------------------------------------------ texto

    static String[] brackets(int seed) {
        return BRACKETS[Math.floorMod(seed, BRACKETS.length)];
    }

    static String smallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            char lower = Character.toLowerCase(ch);
            sb.append(lower >= 'a' && lower <= 'z' ? SMALL_CAPS.charAt(lower - 'a') : ch);
        }
        return sb.toString();
    }

    /** Texto visible sin etiquetas MiniMessage, recortado. */
    static String plain(String s, int max) {
        String t = s.replaceAll("<[^>]*>", "").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    static String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (n.length() > 24) n = n.substring(0, 24).replaceAll("-+$", "");
        return n.isEmpty() ? "item" : n;
    }

    // ------------------------------------------------------------ iconos

    private static String nearest(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < DYE_RGB.length; i++) {
            int dr = r - ((DYE_RGB[i] >> 16) & 255), dg = g - ((DYE_RGB[i] >> 8) & 255), db = b - (DYE_RGB[i] & 255);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return DYE[best];
    }

    static Material dye(String hex) {
        Material m = Material.matchMaterial(nearest(hex) + "_DYE");
        return m != null ? m : Material.WHITE_DYE;
    }

    static Material wool(String hex) {
        Material m = Material.matchMaterial(nearest(hex) + "_WOOL");
        return m != null ? m : Material.WHITE_WOOL;
    }

    // ------------------------------------------------------------ valores por defecto (primer arranque)

    static List<String> defaults(CType t) {
        return switch (t) {
            case PREFIX -> List.of("Rookie|0", "Pro", "Elite", "Legend", "Mythic", "Dragon");
            case TAG -> List.of("VIP|0|#ffaa00", "MVP", "Gamer", "Rey", "Reina", "OP", "Cool", "Noob");
            case NAMECOLOR -> List.of("Verde|0|green", "Azul|300|blue", "Dorado|500|gold", "Rosa|300|pink",
                    "Arcoíris|1500|rainbow", "Fuego|900|red>gold", "Océano|900|aqua>blue", "Aurora|1200");
            case CHATCOLOR -> List.of("Blanco|0|white", "Celeste|300|aqua", "Lima|300|lime", "Atardecer|900|gold>red",
                    "Arcoíris|1500|rainbow", "Galaxia|1200|purple>blue", "Menta|900|green>aqua");
            case JOINMSG -> List.of("¡Ha llegado <player>!", "<player> has joined the party", "Cuidado, entró <player>",
                    "Welcome back, <player>");
            case QUITMSG -> List.of("<player> se fue a dormir", "<player> has left the game", "Hasta pronto, <player>");
            case JOINTITLE -> List.of("Bienvenido;<player>", "Welcome;<player>", "¡Hola de nuevo!;Te extrañábamos");
        };
    }
}
