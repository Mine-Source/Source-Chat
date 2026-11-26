package dev.minesource.sourcechat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Placeholders propios (para tab, scoreboards, otros plugins):
 * mentions, sounds, messages, chat, ignored, muted, channel, game_active, coins, wins, language, nick,
 * displayname, displayname_mini, displayname_plain, prefix, prefix_mini, tag, tag_mini.
 * Las versiones sin sufijo usan formato &amp;#rrggbb (compatible con la mayoría de plugins de tab).
 */
final class SourceExpansion extends PlaceholderExpansion {

    private final SourceChat plugin;

    SourceExpansion(SourceChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sourcechat";
    }

    @Override
    public String getAuthor() {
        return "Mine Source";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("game_active")) return String.valueOf(plugin.games() != null && plugin.games().active());
        if (player == null) return "";
        Profile p = plugin.profiles().of(player);
        switch (key) {
            case "mentions": return String.valueOf(p.mentions);
            case "sounds": return String.valueOf(p.sounds);
            case "messages": return String.valueOf(p.messages);
            case "chat": return String.valueOf(p.chat);
            case "ignored": return String.valueOf(p.ignored.size());
            case "muted": return String.valueOf(p.muted());
            case "channel": return plugin.settings().channel(p.channel).id();
            case "coins": return String.valueOf(plugin.eco().balance(player));
            case "wins": return String.valueOf(p.wins);
            case "language": return p.lang != null ? p.lang : plugin.lang().def();
            default: break;
        }
        Player online = player.getPlayer();
        if (online == null) return "";
        Names names = plugin.names();
        Cosmetics cos = plugin.cosmetics();
        Component c;
        switch (key) {
            case "nick": return p.nick == null ? online.getName() : names.plainName(online, p);
            case "displayname": return names.legacy(names.name(online, p));
            case "displayname_mini": return Text.MM.serialize(names.name(online, p));
            case "displayname_plain": return names.plainName(online, p);
            case "prefix":
            case "prefix_mini":
                c = cos == null ? null : cos.component(p, CType.PREFIX);
                return c == null ? "" : (key.endsWith("mini") ? Text.MM.serialize(c) : names.legacy(c));
            case "tag":
            case "tag_mini":
                c = cos == null ? null : cos.component(p, CType.TAG);
                return c == null ? "" : (key.endsWith("mini") ? Text.MM.serialize(c) : names.legacy(c));
            default:
                return null;
        }
    }
}
