package dev.novachat;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** NovaChat: chat moderno, interactivo y configurable para Paper. */
public final class NovaChat extends JavaPlugin {

    private Settings settings;
    private ProfileStore profiles;
    private TokenService tokens;
    private GameManager games;
    private MenuManager menus;
    private ChatState state;
    private Announcer announcer;
    private Discord discord;
    private boolean papi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        state = new ChatState();
        settings = new Settings(this);
        profiles = new ProfileStore(this);
        profiles.load();
        profiles.start();
        tokens = new TokenService(this);
        tokens.start();
        menus = new MenuManager(this);
        games = new GameManager(this);
        games.start();
        announcer = new Announcer(this);
        announcer.start();
        discord = new Discord(this);
        discord.start();

        papi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papi) {
            new NovaExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado: placeholders activados.");
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        Commands commands = new Commands(this);
        bind(commands, "novachat", "msg", "reply", "ignore");
        ModCommands mod = new ModCommands(this);
        bind(mod, "mute", "unmute", "clearchat", "lockchat", "slowmode", "chatspy", "channel", "broadcast");

        getLogger().info("NovaChat " + getPluginMeta().getVersion() + " listo.");
    }

    private <T extends CommandExecutor & TabCompleter> void bind(T handler, String... names) {
        for (String name : names) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            }
        }
    }

    @Override
    public void onDisable() {
        if (games != null) games.stop();
        if (announcer != null) announcer.stop();
        if (discord != null) discord.stop();
        if (tokens != null) tokens.stop();
        if (profiles != null) profiles.saveNow();
    }

    void reloadAll() {
        reloadConfig();
        settings.reload();
        games.restart();
        announcer.restart();
        discord.restart();
    }

    Settings settings() { return settings; }
    ProfileStore profiles() { return profiles; }
    TokenService tokens() { return tokens; }
    GameManager games() { return games; }
    MenuManager menus() { return menus; }
    ChatState state() { return state; }
    Discord discord() { return discord; }
    boolean papi() { return papi; }
}
