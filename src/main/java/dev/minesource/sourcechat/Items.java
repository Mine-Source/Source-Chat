package dev.minesource.sourcechat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Constructores de ítems para los menús. */
final class Items {

    private Items() {
    }

    static Component plain(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static ItemStack of(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (name != null) meta.displayName(plain(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(Items::plain).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    static ItemStack glow(ItemStack it, boolean on) {
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
            it.setItemMeta(m);
        }
        return it;
    }

    static ItemStack filler(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setHideTooltip(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Cabeza con skin. texture = hash, URL de textures.minecraft.net o valor base64. */
    static ItemStack head(String texture, Component name, List<Component> lore) {
        ItemStack it = of(Material.PLAYER_HEAD, name, lore);
        String b64 = toBase64(texture);
        if (b64 == null) return it;
        try {
            SkullMeta sm = (SkullMeta) it.getItemMeta();
            PlayerProfile prof = Bukkit.createProfile(UUID.nameUUIDFromBytes(b64.getBytes(StandardCharsets.UTF_8)), "source");
            prof.setProperty(new ProfileProperty("textures", b64));
            sm.setPlayerProfile(prof);
            it.setItemMeta(sm);
        } catch (Throwable ignored) {
            // API de perfiles no disponible: se queda la cabeza por defecto
        }
        return it;
    }

    static String toBase64(String t) {
        if (t == null || t.isBlank()) return null;
        String s = t.trim();
        if (s.startsWith("eyJ")) return s;
        String url = s.startsWith("http") ? s : "http://textures.minecraft.net/texture/" + s;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
