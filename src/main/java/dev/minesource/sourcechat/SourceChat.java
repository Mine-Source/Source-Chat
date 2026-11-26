package dev.minesource.sourcechat;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * SourceChat: chat moderno, interactivo y configurable para Paper.
 * Los módulos opcionales (cosméticos, nombres/tab, juegos, anuncios, Discord) se activan y desactivan
 * desde config.yml o el panel de administración; si uno falla se apaga solo y el chat sigue funcionando.
 */
public final class SourceChat extends JavaPlugin {

    static final List<String> MODULES = List.of("cosmetics", "namesync", "games", "announcements", "discord",
            "tokens", "mentions", "joinquit");

    private Lang lang;
    private Settings settings;
    private ProfileStore profiles;
    private Eco eco;
    private Prompts prompts;
    private TokenService tokens;
    private Menus menus;
    private Names names;
    private ChatState state;
    private boolean papi;

    // Módulos opcionales (null = desactivado)
    private Cosmetics cosmetics;
    private QuickSetup quick;
    private GameManager games;
    private Announcer announcer;
    private Discord discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        state = new ChatState();
        lang = new Lang(this);
        lang.load();
        settings = new Settings(this);
        profiles = new ProfileStore(this);
        profiles.load();
        profiles.start();
        eco = new Eco(this);
        prompts = new Prompts(this);
        tokens = new TokenService(this);
        tokens.start();
        menus = new Menus(this);
        names = new Names(this);

        papi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papi) {
            try {
                new SourceExpansion(this).register();
                getLogger().info("PlaceholderAPI detectado: placeholders activados.");
            } catch (Throwable t) {
                papi = false;
                getLogger().log(Level.WARNING, "No se pudo registrar la expansión de PlaceholderAPI", t);
            }
        }

        startModules();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        bind(new AdminCommand(this), "sourcechat");
        bind(new Commands(this), "msg", "reply", "ignore", "togglemsg", "helpop");
        bind(new ModCommands(this), "mute", "unmute", "clearchat", "lockchat", "slowmode", "chatspy", "channel",
                "broadcast", "staffchat", "announce", "chatgame");
        bind(new CosmeticCommands(this), "cosmetics", "chatcolor", "namecolor", "prefix", "tag", "joinmsg",
                "quitmsg", "jointitle", "nick", "realname", "coins", "pay", "lang");

        getLogger().info("SourceChat " + getPluginMeta().getVersion() + " listo · identidad: "
                + (profiles.nameMode() ? "nombre (servidor no premium)" : "UUID") + " · idiomas: " + lang.codes());
    }

    private <T extends CommandExecutor & TabCompleter> void bind(T handler, String... commandNames) {
        for (String name : commandNames) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            }
        }
    }

    @Override
    public void onDisable() {
        stopModules();
        if (tokens != null) tokens.stop();
        if (profiles != null) profiles.saveNow();
    }

    // ------------------------------------------------------------ módulos

    private void startModules() {
        stopModules();
        if (settings.module("cosmetics")) {
            try {
                cosmetics = new Cosmetics(this);
                cosmetics.load();
                quick = new QuickSetup(this);
                quick.start();
            } catch (Throwable t) {
                failModule("cosmetics", t);
            }
        }
        if (settings.module("games")) {
            try {
                games = new GameManager(this);
                games.start();
            } catch (Throwable t) {
                failModule("games", t);
            }
        }
        if (settings.module("announcements")) {
            try {
                announcer = new Announcer(this);
                announcer.start();
            } catch (Throwable t) {
                failModule("announcements", t);
            }
        }
        if (settings.module("discord")) {
            try {
                discord = new Discord(this);
                discord.start();
            } catch (Throwable t) {
                failModule("discord", t);
            }
        }
        if (settings.module("namesync")) {
            try {
                names.applyAll();
            } catch (Throwable t) {
                failModule("namesync", t);
            }
        }
    }

    private void stopModules() {
        if (games != null) games.stop();
        games = null;
        if (announcer != null) announcer.stop();
        announcer = null;
        if (discord != null) discord.stop();
        discord = null;
        if (quick != null) quick.stop();
        quick = null;
        cosmetics = null;
        if (names != null) names.resetAll();
    }

    /** Apaga un módulo que falló (sin guardar en config) para que el resto siga funcionando. */
    void failModule(String module, Throwable t) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(this, () -> failModule(module, t));
            return;
        }
        getLogger().log(Level.SEVERE, "El módulo '" + module + "' falló y fue desactivado; el chat sigue funcionando.", t);
        getConfig().set("modules." + module, false);
        settings.reload();
        switch (module) {
            case "cosmetics" -> {
                if (quick != null) quick.stop();
                quick = null;
                cosmetics = null;
            }
            case "games" -> {
                if (games != null) games.stop();
                games = null;
            }
            case "announcements" -> {
                if (announcer != null) announcer.stop();
                announcer = null;
            }
            case "discord" -> {
                if (discord != null) discord.stop();
                discord = null;
            }
            case "namesync" -> names.resetAll();
            default -> {
            }
        }
    }

    /** Activa o desactiva un módulo y lo guarda en config.yml. */
    void setModule(String module, boolean on) {
        getConfig().set("modules." + module, on);
        saveConfig();
        settings.reload();
        startModules();
    }

    void reloadAll() {
        reloadConfig();
        settings.reload();
        lang.load();
        startModules();
    }

    Lang lang() { return lang; }
    Settings settings() { return settings; }
    ProfileStore profiles() { return profiles; }
    Eco eco() { return eco; }
    Prompts prompts() { return prompts; }
    TokenService tokens() { return tokens; }
    Menus menus() { return menus; }
    Names names() { return names; }
    ChatState state() { return state; }
    Cosmetics cosmetics() { return cosmetics; }
    QuickSetup quick() { return quick; }
    GameManager games() { return games; }
    Announcer announcer() { return announcer; }
    Discord discord() { return discord; }
    boolean papi() { return papi; }
}
