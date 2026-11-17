package dev.novachat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.command.CommandSender;

import java.util.regex.Pattern;

/** Utilidades de texto: MiniMessage completo (config) y MiniMessage restringido (jugadores). */
public final class Text {

    /** Para textos de la configuración (admin de confianza). */
    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Para texto escrito por jugadores: solo colores, estilos, degradados y arcoíris. Sin click/hover. */
    public static final MiniMessage SAFE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /** Convierte el texto de un jugador a Component respetando su permiso novachat.minimessage. */
    public static Component user(CommandSender sender, String raw) {
        return sender.hasPermission("novachat.minimessage") ? SAFE.deserialize(raw) : Component.text(raw);
    }

    /** Convierte códigos legacy (&a, &#rrggbb, §a) devueltos por PlaceholderAPI a MiniMessage. */
    public static String legacyToMini(String s) {
        if (s.indexOf('&') < 0 && s.indexOf('§') < 0) return s;
        s = s.replace('§', '&');
        s = HEX.matcher(s).replaceAll("<#$1>");
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                String tag = tag(Character.toLowerCase(s.charAt(i + 1)));
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String tag(char c) {
        return switch (c) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}
