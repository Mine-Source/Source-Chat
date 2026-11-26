package dev.minesource.sourcechat;

import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Puente chat -> Discord mediante webhook. Agrupa mensajes en lotes para respetar el límite
 * de Discord y nunca bloquea el hilo principal.
 */
final class Discord {

    private final SourceChat plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(300);
    private final AtomicBoolean busy = new AtomicBoolean();
    private BukkitTask task;
    private URI uri;
    private String username = "Source Chat";

    Discord(SourceChat plugin) {
        this.plugin = plugin;
    }

    void start() {
        var c = plugin.getConfig();
        String url = c.getString("discord.webhook-url", "").trim();
        if (url.isEmpty()) return;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("discord.webhook-url no es una URL válida.");
            return;
        }
        username = c.getString("discord.username", "Source Chat");
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::drain, 40L, 30L);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
        queue.clear();
    }

    void restart() {
        stop();
        start();
    }

    /** Encola un mensaje de chat. Seguro desde cualquier hilo. */
    void send(String player, String text) {
        if (task == null) return;
        queue.offer("**" + player + "** › " + text);
    }

    private void drain() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            List<String> batch = new ArrayList<>();
            queue.drainTo(batch, 15);
            if (batch.isEmpty()) return;
            String content = String.join("\n", batch);
            if (content.length() > 1900) content = content.substring(0, 1900);
            String json = "{\"username\":" + quote(username)
                    + ",\"content\":" + quote(content)
                    + ",\"allowed_mentions\":{\"parse\":[]}}";
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 300 && res.statusCode() != 429) {
                plugin.getLogger().warning("Discord respondió HTTP " + res.statusCode());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo enviar a Discord: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            busy.set(false);
        }
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
