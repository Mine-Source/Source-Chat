package dev.minesource.sourcechat;

import org.bukkit.permissions.Permissible;

/**
 * Canal de chat.
 *
 * @param display   texto MiniMessage que aparece en el placeholder &lt;channel&gt; del formato
 * @param permission permiso para escribir y ver el canal ("" = todos)
 * @param radius    radio en bloques (-1 = sin límite)
 * @param worldOnly solo jugadores del mismo mundo
 * @param shortcut  prefijo para escribir rápido en el canal (ej. "!"), "" = ninguno
 * @param format    formato propio del canal ("" = usa chat.formats)
 */
record Channel(String id, String display, String permission, int radius, boolean worldOnly,
               String shortcut, String format) {

    boolean canUse(Permissible who) {
        return permission.isEmpty() || who.hasPermission(permission);
    }
}
