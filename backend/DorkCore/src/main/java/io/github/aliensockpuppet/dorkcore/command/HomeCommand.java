package io.github.aliensockpuppet.dorkcore.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.internal.CoreConfig;
import io.github.aliensockpuppet.dorkcore.internal.store.StoredLocation;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class HomeCommand implements CommandExecutor {
    private static final String DEFAULT_NAME = "home";

    private final CoreServices services;
    private final Guard guard;
    private final Teleports teleports;

    public HomeCommand(CoreServices services, Guard guard, Teleports teleports) {
        this.services = services;
        this.guard = guard;
        this.teleports = teleports;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            services.messages().send(sender, "players-only");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> listHomes(player);
            default -> goHome(player, args);
        };
    }

    private boolean setHome(Player player, String[] args) {
        if (!player.hasPermission("dorkcore.home")) {
            return noPermission(player);
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }
        CoreConfig.Settings settings = services.config().get();
        String name = args.length >= 1 ? args[0] : DEFAULT_NAME;
        if (!HomeAccess.validName(name, settings.maxHomeNameLength())) {
            services.messages().send(player, "home-bad-name");
            services.sounds().play(player, "error");
            return true;
        }
        try {
            boolean replacing = services.homes().has(player.getUniqueId(), name);
            if (!replacing) {
                int cap = HomeAccess.cap(player, settings.defaultHomeCap());
                if (!HomeAccess.unlimited(cap) && services.homes().count(player.getUniqueId()) >= cap) {
                    services.messages().send(player, "home-cap-reached", "%cap%", String.valueOf(cap));
                    services.sounds().play(player, "error");
                    return true;
                }
            }
            services.homes().set(player.getUniqueId(), name, StoredLocation.of(player.getLocation()));
        } catch (SQLException e) {
            return storeError(player, "save the home", e);
        }
        services.messages().send(player, "home-set", "%name%", name);
        services.sounds().play(player, "success");
        return true;
    }

    private boolean goHome(Player player, String[] args) {
        if (!player.hasPermission("dorkcore.home")) {
            return noPermission(player);
        }
        if (!guard.readyAndOffCooldown(player, "home")) {
            return true;
        }
        try {
            List<String> names = services.homes().names(player.getUniqueId());
            if (names.isEmpty()) {
                services.messages().send(player, "home-none");
                services.sounds().play(player, "error");
                return true;
            }
            String name;
            if (args.length >= 1) {
                name = args[0];
            } else if (names.size() == 1) {
                name = names.get(0);
            } else {
                services.messages().send(player, "home-which", "%homes%", String.join(" ", names));
                return true;
            }
            StoredLocation stored = services.homes().get(player.getUniqueId(), name);
            if (stored == null) {
                services.messages().send(player, "home-unknown", "%name%",
                        HomeAccess.validName(name, services.config().get().maxHomeNameLength()) ? name : "?");
                services.sounds().play(player, "error");
                return true;
            }
            Location destination = stored.toLocation();
            if (destination == null) {
                services.messages().send(player, "home-world-missing", "%name%", name);
                services.sounds().play(player, "error");
                return true;
            }
            teleports.teleport(player, destination, "home",
                    services.config().rollCooldownMillis("home", services.config().get().homeCooldownMillis() / 1000L),
                    "home-teleported", "teleport",
                    "%name%", name);
        } catch (SQLException e) {
            return storeError(player, "read the home", e);
        }
        return true;
    }

    private boolean delHome(Player player, String[] args) {
        if (!player.hasPermission("dorkcore.home")) {
            return noPermission(player);
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }
        if (args.length < 1) {
            services.messages().send(player, "delhome-usage");
            return true;
        }
        String name = args[0];
        try {
            if (!services.homes().delete(player.getUniqueId(), name)) {
                services.messages().send(player, "home-unknown", "%name%",
                        HomeAccess.validName(name, services.config().get().maxHomeNameLength()) ? name : "?");
                services.sounds().play(player, "error");
                return true;
            }
        } catch (SQLException e) {
            return storeError(player, "remove the home", e);
        }
        services.messages().send(player, "home-deleted", "%name%", name);
        services.sounds().play(player, "success");
        return true;
    }

    private boolean listHomes(Player player) {
        if (!player.hasPermission("dorkcore.home")) {
            return noPermission(player);
        }
        if (!services.authGate().isAuthenticated(player)) {
            services.messages().send(player, "not-authenticated");
            services.sounds().play(player, "error");
            return true;
        }
        List<String> names;
        try {
            names = services.homes().names(player.getUniqueId());
        } catch (SQLException e) {
            return storeError(player, "list the homes", e);
        }
        if (names.isEmpty()) {
            services.messages().send(player, "home-none");
            return true;
        }
        services.messages().send(player, "homes-header", "%count%", String.valueOf(names.size()));
        player.sendMessage(clickableList(names));
        return true;
    }

    private Component clickableList(List<String> names) {
        Component line = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Component entry = services.colors().action(name)
                    .clickEvent(ClickEvent.runCommand("/home " + name))
                    .hoverEvent(HoverEvent.showText(services.colors().neutral("Teleport to " + name)));
            line = line.append(entry);
            if (i < names.size() - 1) {
                line = line.append(services.colors().neutral("  "));
            }
        }
        return line;
    }

    private boolean noPermission(Player player) {
        services.messages().send(player, "no-permission");
        services.sounds().play(player, "error");
        return true;
    }

    private boolean storeError(Player player, String action, SQLException e) {
        services.messages().send(player, "store-error");
        services.sounds().play(player, "error");
        services.logStoreError(action, e);
        return true;
    }
}
