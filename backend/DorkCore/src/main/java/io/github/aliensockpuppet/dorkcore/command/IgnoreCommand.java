package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IgnoreCommand implements CommandExecutor {
    private static final long DEFAULT_IGNORE_COOLDOWN_SECONDS = 2L;

    private final CoreServices services;

    public IgnoreCommand(CoreServices services) {
        this.services = services;
    }

    private boolean offCooldown(Player player) {
        long left = services.cooldowns().remaining(player.getUniqueId(), "ignore");
        if (left > 0L) {
            long seconds = (left + 999L) / 1000L;
            services.messages().send(player, "cooldown", "%seconds%", String.valueOf(seconds));
            services.sounds().play(player, "error");
            return false;
        }
        return true;
    }

    private void startCooldown(Player player) {
        services.cooldowns().start(player.getUniqueId(), "ignore",
                services.cooldowns().roll("ignore", DEFAULT_IGNORE_COOLDOWN_SECONDS));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("dorkcore.ignore")) {
            services.messages().send(player, "no-permission");
            services.sounds().play(player, "error");
            return true;
        }

        String name = command.getName().toLowerCase();
        return switch (name) {
            case "ignorelist" -> list(player);
            case "unignore" -> unignore(player, args);
            default -> ignore(player, args);
        };
    }

    private boolean ignore(Player player, String[] args) {
        if (args.length < 1) {
            services.messages().send(player, "ignore-usage");
            return true;
        }
        String targetName = strip(args[0]);
        UUID targetId = resolve(targetName);
        if (targetId == null || targetId.equals(player.getUniqueId())) {
            services.messages().send(player, "ignore-self-or-unknown");
            services.sounds().play(player, "error");
            return true;
        }
        if (!offCooldown(player)) {
            return true;
        }
        try {
            services.ignores().add(player.getUniqueId(), targetId);
        } catch (SQLException e) {
            services.logStoreError("add an ignore", e);
            services.messages().send(player, "store-error");
            return true;
        }
        startCooldown(player);
        services.messages().send(player, "ignore-added", "%player%", targetName);
        services.sounds().play(player, "success");
        return true;
    }

    private boolean unignore(Player player, String[] args) {
        if (args.length < 1) {
            services.messages().send(player, "unignore-usage");
            return true;
        }
        String targetName = strip(args[0]);
        UUID targetId = resolve(targetName);
        if (targetId == null) {
            services.messages().send(player, "ignore-self-or-unknown");
            services.sounds().play(player, "error");
            return true;
        }
        if (!offCooldown(player)) {
            return true;
        }
        try {
            services.ignores().remove(player.getUniqueId(), targetId);
        } catch (SQLException e) {
            services.logStoreError("remove an ignore", e);
            services.messages().send(player, "store-error");
            return true;
        }
        startCooldown(player);
        services.messages().send(player, "ignore-removed", "%player%", targetName);
        services.sounds().play(player, "success");
        return true;
    }

    private boolean list(Player player) {
        List<UUID> ids;
        try {
            ids = services.ignores().list(player.getUniqueId());
        } catch (SQLException e) {
            services.logStoreError("read the ignore list", e);
            services.messages().send(player, "store-error");
            return true;
        }
        if (ids.isEmpty()) {
            services.messages().send(player, "ignore-list-empty");
            return true;
        }
        String names = ids.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(OfflinePlayer::getName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining(", "));
        services.messages().send(player, "ignore-list", "%players%", names);
        return true;
    }

    private static UUID resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.getUniqueId();
    }

    private static String strip(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "").trim();
    }
}
