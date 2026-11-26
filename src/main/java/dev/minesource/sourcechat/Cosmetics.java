package dev.minesource.sourcechat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tienda de cosméticos: prefijos, tags, color de nombre, color de chat, mensajes y títulos de entrada/salida.
 * Cada tipo vive en plugins/SourceChat/cosmetics/&lt;tipo&gt;.yml. Es un módulo opcional: si se desactiva
 * (o falla) el chat sigue funcionando sin consumir recursos.
 */
final class Cosmetics {

    record Item(CType type, String id, String name, String value, String subtitle, long price,
                Material icon, String rarity, String permission, boolean enabled) {
        String key() {
            return type.id + ":" + id;
        }

        Item withPrice(long p) {
            return new Item(type, id, name, value, subtitle, p, icon, rarity, permission, enabled);
        }

        Item withEnabled(boolean e) {
            return new Item(type, id, name, value, subtitle, price, icon, rarity, permission, e);
        }
    }

    enum Result { OK, ALREADY, NOT_ENOUGH, DISABLED }

    private final SourceChat plugin;
    private volatile Map<CType, Map<String, Item>> items = new EnumMap<>(CType.class);

    Cosmetics(SourceChat plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ carga y guardado

    void load() {
        Map<CType, Map<String, Item>> m = new EnumMap<>(CType.class);
        File dir = new File(plugin.getDataFolder(), "cosmetics");
        dir.mkdirs();
        for (CType t : CType.values()) {
            File f = new File(dir, t.id + ".yml");
            Map<String, Item> map = new LinkedHashMap<>();
            if (!f.exists()) {
                for (Item it : QuickSetup.build(plugin, t, Styler.defaults(t), Set.of())) map.put(it.id(), it);
                write(t, map);
            } else {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                ConfigurationSection sec = y.getConfigurationSection("items");
                if (sec != null) {
                    for (String id : sec.getKeys(false)) {
                        ConfigurationSection s = sec.getConfigurationSection(id);
                        Item it = s == null ? null : read(t, id.toLowerCase(Locale.ROOT), s);
                        if (it != null) map.put(it.id(), it);
                    }
                }
            }
            m.put(t, map);
        }
        items = m;
    }

    private static Item read(CType t, String id, ConfigurationSection s) {
        String value = s.getString("value", "");
        if (value.isEmpty()) return null;
        Material icon = Material.matchMaterial(s.getString("icon", t.icon.name()));
        if (icon == null) icon = t.icon;
        String rarity = s.getString("rarity", "common").toLowerCase(Locale.ROOT);
        if (!Styler.isRarity(rarity)) rarity = "common";
        return new Item(t, id, s.getString("name", id), value, s.getString("subtitle", ""),
                Math.max(0, s.getLong("price", 0)), icon, rarity,
                s.getString("permission", ""), s.getBoolean("enabled", true));
    }

    private void write(CType t, Map<String, Item> map) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("SourceChat · " + t.id, "Edita a mano o usa /sourcechat cosmetic y /sourcechat quick."));
        for (Item it : map.values()) {
            String b = "items." + it.id() + ".";
            y.set(b + "name", it.name());
            y.set(b + "value", it.value());
            if (!it.subtitle().isEmpty()) y.set(b + "subtitle", it.subtitle());
            y.set(b + "price", it.price());
            y.set(b + "icon", it.icon().name());
            y.set(b + "rarity", it.rarity());
            if (!it.permission().isEmpty()) y.set(b + "permission", it.permission());
            y.set(b + "enabled", it.enabled());
        }
        try {
            y.save(new File(plugin.getDataFolder(), "cosmetics/" + t.id + ".yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar cosmetics/" + t.id + ".yml: " + ex.getMessage());
        }
    }

    private synchronized void update(CType t, Consumer<Map<String, Item>> fn) {
        Map<CType, Map<String, Item>> copy = new EnumMap<>(CType.class);
        copy.putAll(items);
        Map<String, Item> m = new LinkedHashMap<>(items.getOrDefault(t, Map.of()));
        fn.accept(m);
        copy.put(t, m);
        items = copy;
        write(t, m);
    }

    // ------------------------------------------------------------ consulta

    Item get(CType t, String id) {
        return id == null ? null : items.getOrDefault(t, Map.of()).get(id.toLowerCase(Locale.ROOT));
    }

    List<Item> list(CType t, boolean onlyEnabled) {
        List<Item> out = new ArrayList<>();
        for (Item it : items.getOrDefault(t, Map.of()).values()) if (!onlyEnabled || it.enabled()) out.add(it);
        return out;
    }

    Set<String> ids(CType t) {
        return Set.copyOf(items.getOrDefault(t, Map.of()).keySet());
    }

    // ------------------------------------------------------------ administración

    void addAll(CType t, Collection<Item> add) {
        update(t, m -> add.forEach(it -> m.put(it.id(), it)));
    }

    boolean remove(CType t, String id) {
        boolean[] removed = {false};
        update(t, m -> removed[0] = m.remove(id.toLowerCase(Locale.ROOT)) != null);
        return removed[0];
    }

    boolean setPrice(CType t, String id, long price) {
        Item it = get(t, id);
        if (it == null) return false;
        update(t, m -> m.put(it.id(), it.withPrice(Math.max(0, price))));
        return true;
    }

    /** @return nuevo estado, o null si no existe. */
    Boolean toggle(CType t, String id) {
        Item it = get(t, id);
        if (it == null) return null;
        boolean next = !it.enabled();
        update(t, m -> m.put(it.id(), it.withEnabled(next)));
        return next;
    }

    // ------------------------------------------------------------ propiedad y equipamiento

    boolean owns(Player p, Profile pr, Item it) {
        return it.price() <= 0
                || (!it.permission().isEmpty() && p.hasPermission(it.permission()))
                || pr.owned.contains(it.key())
                || pr.testing.contains(it.key());
    }

    Result buy(Player p, Item it) {
        Profile pr = plugin.profiles().of(p);
        if (!it.enabled()) return Result.DISABLED;
        if (owns(p, pr, it)) return Result.ALREADY;
        if (!plugin.eco().withdraw(p, it.price())) return Result.NOT_ENOUGH;
        pr.owned.add(it.key());
        plugin.profiles().touch();
        return Result.OK;
    }

    void equip(Player p, Item it) {
        Profile pr = plugin.profiles().of(p);
        pr.equipped.put(it.type().id, it.id());
        plugin.profiles().touch();
        plugin.names().apply(p);
    }

    void unequip(Player p, CType t) {
        Profile pr = plugin.profiles().of(p);
        pr.equipped.remove(t.id);
        plugin.profiles().touch();
        plugin.names().apply(p);
    }

    Item equipped(Profile pr, CType t) {
        Item it = get(t, pr.equipped.get(t.id));
        return it != null && it.enabled() ? it : null;
    }

    /** Quita lo equipado que el jugador ya no posea (permiso retirado, cosmético desactivado...). */
    void sanitize(Player p, Profile pr) {
        for (CType t : CType.values()) {
            String id = pr.equipped.get(t.id);
            if (id == null) continue;
            Item it = get(t, id);
            if (it == null || !it.enabled() || !owns(p, pr, it)) {
                pr.equipped.remove(t.id);
                plugin.profiles().touch();
            }
        }
    }

    // ------------------------------------------------------------ renderizado

    /** Cómo se ve el cosmético (para menús y vistas previas). */
    Component render(Item it, String player) {
        TagResolver pl = Placeholder.parsed("player", player);
        return switch (it.type()) {
            case NAMECOLOR, CHATCOLOR -> Text.MM.deserialize(it.value() + Text.MM.escapeTags(it.name()), pl);
            default -> Text.MM.deserialize(it.value(), pl);
        };
    }

    Component component(Profile pr, CType t) {
        Item it = equipped(pr, t);
        return it == null ? null : Text.MM.deserialize(it.value());
    }

    /** Componente para el chat: el cosmético seguido de un espacio, o vacío. */
    Component slot(Profile pr, CType t) {
        Component c = component(pr, t);
        return c == null ? Component.empty() : c.append(Component.space());
    }

    String chatStyle(Profile pr) {
        Item it = equipped(pr, CType.CHATCOLOR);
        return it == null ? null : it.value();
    }

    String nameStyle(Profile pr) {
        Item it = equipped(pr, CType.NAMECOLOR);
        return it == null ? null : it.value();
    }

    Component joinMessage(Player p, Profile pr) {
        return message(CType.JOINMSG, p, pr);
    }

    Component quitMessage(Player p, Profile pr) {
        return message(CType.QUITMSG, p, pr);
    }

    private Component message(CType t, Player p, Profile pr) {
        Item it = equipped(pr, t);
        if (it == null) return null;
        return Text.MM.deserialize(it.value(), Placeholder.component("player", plugin.names().name(p, pr)));
    }

    void showTitle(Player target, Item it, String playerName) {
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(800));
        TagResolver pl = Placeholder.parsed("player", playerName);
        Component sub = it.subtitle().isEmpty() ? Component.empty() : Text.MM.deserialize(it.subtitle(), pl);
        target.showTitle(Title.title(Text.MM.deserialize(it.value(), pl), sub, times));
    }

    /** Título de entrada: lo ve el propio jugador o todos, según cosmetics.jointitle-audience. */
    void joinTitle(Player p, Profile pr) {
        Item it = equipped(pr, CType.JOINTITLE);
        if (it == null) return;
        if (plugin.getConfig().getString("cosmetics.jointitle-audience", "self").equalsIgnoreCase("all")) {
            for (Player v : Bukkit.getOnlinePlayers()) showTitle(v, it, p.getName());
        } else {
            showTitle(p, it, p.getName());
        }
    }

    /** Vista previa para quien está mirando la tienda. */
    void preview(Player viewer, Item it) {
        Settings st = plugin.settings();
        Component shown = render(it, viewer.getName());
        switch (it.type()) {
            case JOINTITLE -> showTitle(viewer, it, viewer.getName());
            case CHATCOLOR -> viewer.sendMessage(st.fmt(viewer, "preview-line",
                    Placeholder.component("cosmetic", Component.empty()),
                    Placeholder.component("text", Text.MM.deserialize(it.value()
                            + Text.MM.escapeTags(st.raw(viewer, "preview-sample"))))));
            case NAMECOLOR, PREFIX, TAG -> viewer.sendMessage(st.fmt(viewer, "preview-line",
                    Placeholder.component("cosmetic", shown),
                    Placeholder.component("text", Text.MM.deserialize(Text.MM.escapeTags(st.raw(viewer, "preview-sample"))))));
            default -> viewer.sendMessage(st.fmt(viewer, "preview-line",
                    Placeholder.component("cosmetic", Component.empty()),
                    Placeholder.component("text", shown)));
        }
    }
}
