package io.github.aliensockpuppet.dorkcore.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.PlayerToggles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageCommand implements CommandExecutor {
    private static final int MAX_LENGTH = 256;

    private static final long DEFAULT_MSG_COOLDOWN_SECONDS = 1L;

    private final CoreServices services;

    private final Map<UUID, UUID> replyTarget = new ConcurrentHashMap<>();

    public MessageCommand(CoreServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.msg")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }

        boolean reply = command.getName().equalsIgnoreCase("r") || command.getName().equalsIgnoreCase("reply");
        return reply ? doReply(player, args) : doMsg(player, args);
    }

    private boolean doMsg(Player sender, String[] args) {
        if (args.length < 2) {
            services.messages().send(sender, "msg-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        String body = join(args, 1);
        send(sender, target, body);
        return true;
    }

    private boolean doReply(Player sender, String[] args) {
        if (args.length < 1) {
            services.messages().send(sender, "reply-usage");
            return true;
        }
        UUID targetId = replyTarget.get(sender.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            services.messages().send(sender, "reply-none");
            services.sounds().play(sender, "error");
            return true;
        }
        send(sender, target, join(args, 0));
        return true;
    }

    private void send(Player sender, Player target, String rawBody) {
        UUID id = sender.getUniqueId();
        long left = services.cooldowns().remaining(id, "msg");
        if (left > 0L) {
            long seconds = (left + 999L) / 1000L;
            services.messages().send(sender, "cooldown", "%seconds%", String.valueOf(seconds));
            services.sounds().play(sender, "error");
            return;
        }
        if (target == null || !target.isOnline() || target.equals(sender)) {
            services.messages().send(sender, "msg-not-delivered");
            services.sounds().play(sender, "error");
            return;
        }
        if (!services.playerToggles().isOn(target.getUniqueId(), PlayerToggles.Toggle.RECEIVE_PMS)) {
            services.messages().send(sender, "msg-not-delivered");
            services.sounds().play(sender, "error");
            return;
        }

        String body = sanitize(rawBody);
        if (body.isEmpty()) {
            services.messages().send(sender, "msg-empty");
            services.sounds().play(sender, "error");
            return;
        }

        services.cooldowns().start(id, "msg", services.cooldowns().roll("msg", DEFAULT_MSG_COOLDOWN_SECONDS));

        if (isIgnoredBy(target, sender)) {
            sendFormatted(sender, "msg-to", target.getName(), body);
            services.sounds().play(sender, "tpa-sent");
            return;
        }

        sendFormatted(sender, "msg-to", target.getName(), body);
        sendFormatted(target, "msg-from", sender.getName(), body);
        services.sounds().play(sender, "tpa-sent");
        services.sounds().play(target, "tpa-received");

        replyTarget.put(sender.getUniqueId(), target.getUniqueId());
        replyTarget.put(target.getUniqueId(), sender.getUniqueId());
    }

    private void sendFormatted(Player to, String key, String otherName, String body) {
        String format = services.messages().raw(key, "<gray>[<green><player><gray>] ");
        String safeName = stripMarkup(otherName);
        String filled = format.replace("%player%", safeName).replace("<player>", safeName);
        Component prefix = services.colors().render(filled);
        to.sendMessage(prefix.append(Component.text(body)));
    }

    private static String stripMarkup(String name) {
        if (name == null) {
            return "";
        }
        return name.replace('§', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ').trim();
    }

    private boolean isIgnoredBy(Player target, Player sender) {
        try {
            return services.ignores().ignores(target.getUniqueId(), sender.getUniqueId());
        } catch (java.sql.SQLException e) {
            services.logStoreError("check an ignore", e);
            return false;
        }
    }

    public void forget(UUID player) {
        replyTarget.remove(player);
        replyTarget.values().removeIf(v -> v.equals(player));
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw
                .replace('§', ' ')
                .replace('<', ' ')
                .replace('>', ' ')
                .replace('&', ' ');
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > MAX_LENGTH) {
            out = out.substring(0, MAX_LENGTH);
        }
        return out;
    }
}
