package io.github.aliensockpuppet.dorkcore.command;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class ChatListener implements Listener {
    private final CoreServices services;

    private record LastLine(String normalised, long blockedUntil) {
    }

    private final Map<UUID, LastLine> lastLine = new ConcurrentHashMap<>();

    private static final long DEFAULT_CHAT_SECONDS = 2L;
    private static final long DEFAULT_REPEAT_SECONDS = 30L;
    private static final long REPEAT_FLOOR_MIN_MILLIS = 15_000L;
    private static final long REPEAT_FLOOR_MAX_MILLIS = 30_000L;

    private volatile boolean warnedImmutable = false;

    public ChatListener(CoreServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChatSpam(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("dorkcore.chat.bypass")) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        long left = services.cooldowns().remaining(id, "chat");
        if (left > 0L) {
            event.setCancelled(true);
            notice(player, "chat-slow", "<red>Slow down. Wait <seconds>s",
                    String.valueOf((left + 999L) / 1000L));
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String normalised = normalise(plain);
        LastLine last = lastLine.get(id);
        if (last != null && now < last.blockedUntil() && last.normalised().equals(normalised)) {
            event.setCancelled(true);
            notice(player, "chat-repeat", "<red>You already said that. Wait <seconds>s",
                    String.valueOf((last.blockedUntil() - now + 999L) / 1000L));
            return;
        }

        services.cooldowns().start(id, "chat", services.cooldowns().roll("chat", DEFAULT_CHAT_SECONDS));
        long repeatFloor = ThreadLocalRandom.current().nextLong(REPEAT_FLOOR_MIN_MILLIS, REPEAT_FLOOR_MAX_MILLIS + 1L);
        long repeatMillis = Math.max(services.cooldowns().roll("chat-repeat", DEFAULT_REPEAT_SECONDS), repeatFloor);
        lastLine.put(id, new LastLine(normalised, now + repeatMillis));
    }

    private static String normalise(String raw) {
        String out = raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        return out.isEmpty() ? raw.trim() : out;
    }

    private void notice(Player player, String key, String fallback, String seconds) {
        if (services.messages().has(key)) {
            services.messages().send(player, key, "%seconds%", seconds);
        } else {
            player.sendMessage(services.colors().render(fallback.replace("<seconds>", seconds)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        long now = System.currentTimeMillis();
        lastLine.values().removeIf(last -> last.blockedUntil() <= now);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        PlayerToggles toggles = services.playerToggles();

        List<Audience> hidden = new ArrayList<>();
        for (Audience viewer : event.viewers()) {
            if (!(viewer instanceof Player p)) {
                continue;
            }
            if (p.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }
            if (!toggles.isOn(p.getUniqueId(), PlayerToggles.Toggle.CHAT)) {
                hidden.add(viewer);
            }
        }
        if (hidden.isEmpty()) {
            return;
        }
        try {
            event.viewers().removeAll(hidden);
        } catch (UnsupportedOperationException e) {
            if (!warnedImmutable) {
                warnedImmutable = true;
                services.plugin().getLogger().log(Level.WARNING,
                        "Chat viewer set is read-only (another chat plugin replaced it), "
                                + "so the Public chat toggle cannot hide messages. "
                                + "This is logged once.", e);
            }
        }
    }
}
