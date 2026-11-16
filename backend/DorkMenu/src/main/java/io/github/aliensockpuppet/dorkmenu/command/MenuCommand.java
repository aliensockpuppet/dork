package io.github.aliensockpuppet.dorkmenu.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.dorkmenu.DorkMenuPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class MenuCommand implements TabExecutor {
    private final DorkMenuPlugin plugin;
    private final DorkCoreApi core;

    public MenuCommand(DorkMenuPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    public BasicCommand asBasicCommand() {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                run(source.getSender(), args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                return complete(source.getSender(), args);
            }
        };
    }

    public BasicCommand screenCommand(String id) {
        return new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                if (!(source.getSender() instanceof Player player)) {
                    core.messages().send(source.getSender(), "players-only");
                    return;
                }
                if (CommandThrottle.blocked(core, player, CommandThrottle.key("dorkmenu", id))) {
                    return;
                }
                if ("duel".equals(id) && args.length > 0) {
                    player.performCommand("rtpqueue:rtpqueue " + String.join(" ", args));
                    return;
                }
                plugin.menu().dispatch(player, id);
            }
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        run(sender, args);
        return true;
    }

    private void run(CommandSender sender, String[] args) {
        if (CommandThrottle.blocked(core, sender, CommandThrottle.key("dorkmenu", "dork"))) {
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("dorkmenu.admin")) {
                core.messages().send(sender, "no-permission");
                return;
            }
            plugin.reloadFiles();
            core.messages().send(sender, "reload-done");
            return;
        }
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("dorkmenu.use")) {
            core.messages().send(player, "no-permission");
            core.sounds().play(player, "error");
            return;
        }
        switch (sub) {
            case "" -> {
                plugin.menu().openMain(player);
                core.sounds().play(player, "menu-open");
            }
            case "homes" -> plugin.menu().dispatch(player, "homes");
            case "settings" -> plugin.menu().dispatch(player, "settings");
            case "rtpqueue", "queue", "duel" -> plugin.menu().dispatch(player, "duel");
            case "lb", "leaderboards" -> plugin.menu().dispatch(player, "stats");
            case "spawn", "rtp", "stats", "map", "store", "discord", "suicide"
                    -> plugin.menu().dispatch(player, sub);
            default -> {
                plugin.menu().openMain(player);
                core.sounds().play(player, "menu-open");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.copyOf(complete(sender, args));
    }

    private Collection<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new java.util.ArrayList<>(List.of("homes", "settings", "rtpqueue", "stats", "store"));
        if (sender.hasPermission("dorkmenu.admin")) {
            options.add("reload");
        }
        return options.stream().filter(o -> o.startsWith(prefix)).toList();
    }
}
