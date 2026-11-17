package dev.novachat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/** Placeholders propios: %novachat_mentions%, %novachat_sounds%, %novachat_messages%, %novachat_chat%, %novachat_ignored%, %novachat_game_active%. */
final class NovaExpansion extends PlaceholderExpansion {

    private final NovaChat plugin;

    NovaExpansion(NovaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "novachat";
    }

    @Override
    public String getAuthor() {
        return "NovaChat";
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
        if (key.equals("game_active")) return String.valueOf(plugin.games().active());
        if (player == null) return "";
        Profile p = plugin.profiles().get(player.getUniqueId());
        return switch (key) {
            case "mentions" -> String.valueOf(p.mentions);
            case "sounds" -> String.valueOf(p.sounds);
            case "messages" -> String.valueOf(p.messages);
            case "chat" -> String.valueOf(p.chat);
            case "ignored" -> String.valueOf(p.ignored.size());
            case "muted" -> String.valueOf(p.muted());
            case "channel" -> plugin.settings().channel(p.channel).id();
            default -> null;
        };
    }
}
