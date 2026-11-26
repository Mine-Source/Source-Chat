package dev.minesource.sourcechat;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Aislado en su propia clase: solo se carga si PlaceholderAPI está instalado. */
final class PapiHook {

    private PapiHook() {
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
