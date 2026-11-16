package io.github.aliensockpuppet.dorkcore.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class AnonMessageCommand implements CommandExecutor {
    private static final long DEFAULT_COOLDOWN_SECONDS = 15L;
    private static final long FLOOR_MIN_MILLIS = 10_000L;
    private static final long FLOOR_MAX_MILLIS = 20_000L;

    private final CoreServices services;

    public AnonMessageCommand(CoreServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.anonmsg")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }
        if (args.length < 2) {
            say(player, "anonmsg-usage", "<gray>Use /anonmsg \\<player> \\<message>");
            return true;
        }

        UUID id = player.getUniqueId();
        long left = services.cooldowns().remaining(id, "anonmsg");
        if (left > 0L) {
            services.messages().send(player, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
            services.sounds().play(player, "error");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline() || target.equals(player)
                || !services.playerToggles().isOn(target.getUniqueId(), PlayerToggles.Toggle.RECEIVE_PMS)) {
            services.messages().send(player, "msg-not-delivered");
            services.sounds().play(player, "error");
            return true;
        }

        String body = MessageCommand.sanitize(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        if (body.isEmpty()) {
            services.messages().send(player, "msg-empty");
            services.sounds().play(player, "error");
            return true;
        }

        services.cooldowns().start(id, "anonmsg", cooldownMillis());

        services.plugin().getLogger().info("[anonmsg] " + player.getName() + " -> " + target.getName() + ": " + body);

        String safeTarget = stripMarkup(target.getName());
        sendFormatted(player, "anonmsg-to",
                "<gray>[me <green>-> <green>" + safeTarget + "<gray>, anonymous] ", safeTarget, body);
        services.sounds().play(player, "tpa-sent");

        if (isIgnoredBy(target, player)) {
            return true;
        }
        sendFormatted(target, "anonmsg-from", "<gray>[<dark_gray>Anonymous <green>-> <gray>me] ", "", body);
        services.sounds().play(target, "tpa-received");
        return true;
    }

    private long cooldownMillis() {
        long rolled = services.cooldowns().roll("anonmsg", DEFAULT_COOLDOWN_SECONDS);
        long floor = ThreadLocalRandom.current().nextLong(FLOOR_MIN_MILLIS, FLOOR_MAX_MILLIS + 1L);
        return Math.max(rolled, floor);
    }

    private void sendFormatted(Player to, String key, String fallback, String name, String body) {
        String format = services.messages().has(key) ? services.messages().raw(key, fallback) : fallback;
        String filled = format.replace("%player%", name).replace("<player>", name);
        Component prefix = services.colors().render(filled);
        to.sendMessage(prefix.append(Component.text(body)));
    }

    private void say(Player to, String key, String fallback) {
        if (services.messages().has(key)) {
            services.messages().send(to, key);
        } else {
            to.sendMessage(services.colors().render(fallback));
        }
    }

    private boolean isIgnoredBy(Player target, Player sender) {
        try {
            return services.ignores().ignores(target.getUniqueId(), sender.getUniqueId());
        } catch (java.sql.SQLException e) {
            services.logStoreError("check an ignore", e);
            return false;
        }
    }

    private static String stripMarkup(String name) {
        return name == null ? "" : name.replace('§', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ').trim();
    }
}
