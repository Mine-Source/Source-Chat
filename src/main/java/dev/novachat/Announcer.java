package dev.novachat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Anuncios automáticos rotativos. */
final class Announcer {

    private final NovaChat plugin;
    private BukkitTask task;
    private int index;

    Announcer(NovaChat plugin) {
        this.plugin = plugin;
    }

    void start() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("announcements.enabled", false)) return;
        long ticks = Math.max(10, c.getLong("announcements.interval-seconds", 300)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcast, ticks, ticks);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    void restart() {
        stop();
        start();
    }

    private void broadcast() {
        FileConfiguration c = plugin.getConfig();
        List<String> list = c.getStringList("announcements.messages");
        if (list.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        String msg;
        if (c.getBoolean("announcements.random", false)) {
            msg = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        } else {
            msg = list.get(index++ % list.size());
        }
        Bukkit.broadcast(Text.MM.deserialize(c.getString("announcements.prefix", "") + msg));
    }
}
