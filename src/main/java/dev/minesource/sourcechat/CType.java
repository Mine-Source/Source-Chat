package dev.minesource.sourcechat;

import org.bukkit.Material;

import java.util.Locale;

/** Tipos de cosméticos de chat. */
enum CType {
    PREFIX("prefix", Material.NAME_TAG, false, "prefix", "prefixes", "prefijo", "prefijos"),
    TAG("tag", Material.OAK_SIGN, false, "tag", "tags", "etiqueta", "etiquetas"),
    NAMECOLOR("namecolor", Material.WHITE_WOOL, false, "namecolor", "namecolors", "colornombre", "colornombres", "name", "nombre"),
    CHATCOLOR("chatcolor", Material.WHITE_DYE, false, "chatcolor", "chatcolors", "colorchat", "colorchats", "color", "colors", "colores"),
    JOINMSG("joinmsg", Material.OAK_DOOR, true, "joinmsg", "join", "welcome", "bienvenida", "bienvenidas", "entrada"),
    QUITMSG("quitmsg", Material.IRON_DOOR, true, "quitmsg", "quit", "leave", "farewell", "despedida", "despedidas", "salida"),
    JOINTITLE("jointitle", Material.ENCHANTED_BOOK, true, "jointitle", "title", "titles", "titulo", "titulos", "títulos");

    final String id;
    final Material icon;
    /** true = el texto de la línea es un mensaje completo (se separa con // en listas). */
    final boolean message;
    private final String[] aliases;

    CType(String id, Material icon, boolean message, String... aliases) {
        this.id = id;
        this.icon = icon;
        this.message = message;
        this.aliases = aliases;
    }

    static CType parse(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT).trim();
        for (CType t : values()) {
            for (String a : t.aliases) if (a.equals(k)) return t;
        }
        return null;
    }
}
