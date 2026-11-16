package io.github.aliensockpuppet.dorkcore.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ToggleCommand implements TabExecutor {
    private static final long DEFAULT_COOLDOWN_SECONDS = 3L;
    private static final long FLOOR_MIN_MILLIS = 2_000L;
    private static final long FLOOR_MAX_MILLIS = 4_000L;

    private static final String LIST_KEY = "toggle-list";
    private static final long LIST_DEFAULT_SECONDS = 2L;
    private static final long LIST_FLOOR_MIN_MILLIS = 1_000L;
    private static final long LIST_FLOOR_MAX_MILLIS = 2_000L;

    private static final Map<String, String> SHORT_NAMES = Map.of("tp", "tparequests");

    private record Entry(PlayerToggles.Toggle toggle, String messageStem, String label) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    static {
        ENTRIES.put("tparequests", new Entry(PlayerToggles.Toggle.TPA_REQUESTS, "tpa-requests", "Teleport requests"));
        ENTRIES.put("tpauto", new Entry(PlayerToggles.Toggle.TPA_AUTO, "tpa-auto", "Auto-accept teleports"));
        ENTRIES.put("chat", new Entry(PlayerToggles.Toggle.CHAT, "chat", "Public chat"));
        ENTRIES.put("pms", new Entry(PlayerToggles.Toggle.RECEIVE_PMS, "receive-pms", "Private messages"));
        ENTRIES.put("joins", new Entry(PlayerToggles.Toggle.JOIN_LEAVE_MESSAGES, "join-leave", "Join and leave messages"));
        ENTRIES.put("deaths", new Entry(PlayerToggles.Toggle.DEATH_MESSAGES, "death-messages", "Death messages"));
        ENTRIES.put("queuealerts", new Entry(PlayerToggles.Toggle.QUEUE_ALERTS, "queue-alerts", "Queue alerts"));
        ENTRIES.put("phantoms", new Entry(PlayerToggles.Toggle.PHANTOM_SPAWNS, "phantom-spawns", "Phantom spawns"));
        ENTRIES.put("style", new Entry(PlayerToggles.Toggle.MESSAGE_STYLE, "message-style", "Modern messages and sounds"));
    }

    private final CoreServices services;

    public ToggleCommand(CoreServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.toggle")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }

        UUID id = player.getUniqueId();
        if (args.length == 0) {
            listThrottled(player, id, false);
            return true;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Entry entry = ENTRIES.get(SHORT_NAMES.getOrDefault(name, name));
        if (entry == null) {
            listThrottled(player, id, true);
            return true;
        }

        long left = services.cooldowns().remaining(id, "toggle");
        if (left > 0L) {
            services.messages().send(player, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
            services.sounds().play(player, "error");
            return true;
        }
        services.cooldowns().start(id, "toggle", cooldownMillis());

        boolean on = services.playerToggles().flip(id, entry.toggle());
        services.messages().send(player, entry.messageStem() + (on ? "-on" : "-off"));
        services.sounds().play(player, on ? "toggle-on" : "toggle-off");
        return true;
    }

    private void listThrottled(Player player, UUID id, boolean unknownName) {
        long left = services.cooldowns().remaining(id, LIST_KEY);
        if (left > 0L) {
            services.messages().send(player, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
            services.sounds().play(player, "error");
            return;
        }
        long rolled = services.cooldowns().roll(LIST_KEY, LIST_DEFAULT_SECONDS);
        long floor = ThreadLocalRandom.current().nextLong(LIST_FLOOR_MIN_MILLIS, LIST_FLOOR_MAX_MILLIS + 1L);
        services.cooldowns().start(id, LIST_KEY, Math.max(rolled, floor));
        list(player);
        if (unknownName) {
            services.sounds().play(player, "error");
        }
    }

    private void list(Player player) {
        UUID id = player.getUniqueId();
        player.sendMessage(services.colors().render("<gray>Use /toggle <green>name<gray>. Toggles:"));
        for (Map.Entry<String, Entry> e : ENTRIES.entrySet()) {
            boolean on = services.playerToggles().isOn(id, e.getValue().toggle());
            Component line = services.colors().neutral("  " + e.getKey() + "  ")
                    .append(on ? services.colors().action("on") : services.colors().destructive("off"))
                    .append(services.colors().neutral("  " + e.getValue().label()));
            player.sendMessage(line);
        }
    }

    private long cooldownMillis() {
        long rolled = services.cooldowns().roll("toggle", DEFAULT_COOLDOWN_SECONDS);
        long floor = ThreadLocalRandom.current().nextLong(FLOOR_MIN_MILLIS, FLOOR_MAX_MILLIS + 1L);
        return Math.max(rolled, floor);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : ENTRIES.keySet()) {
            if (name.startsWith(prefix)) {
                out.add(name);
            }
        }
        return out;
    }
}
