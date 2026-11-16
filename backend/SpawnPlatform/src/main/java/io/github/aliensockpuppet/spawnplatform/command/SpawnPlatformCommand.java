package io.github.aliensockpuppet.spawnplatform.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import io.github.aliensockpuppet.spawnplatform.SpawnPlatformPlugin;
import io.github.aliensockpuppet.spawnplatform.config.Settings;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SpawnPlatformCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("reload", "rebuild", "clear", "setcenter", "info");

    private final SpawnPlatformPlugin plugin;
    private final DorkCoreApi core;

    public SpawnPlatformCommand(SpawnPlatformPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spawnplatform.admin")) {
            core.messages().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadSettings();
                core.messages().send(sender, "sp-reloaded");
            }
            case "rebuild" -> {
                boolean ok = plugin.service().buildPlatform(plugin.settings());
                core.messages().send(sender, ok ? "sp-rebuilt" : "sp-no-world");
            }
            case "clear" -> {
                boolean ok = plugin.service().clearArea(plugin.settings());
                core.messages().send(sender, ok ? "sp-cleared" : "sp-clear-busy");
            }
            case "setcenter" -> {
                if (!(sender instanceof Player player)) {
                    core.messages().send(sender, "players-only");
                    return true;
                }
                plugin.setCenterHere(player);
                core.messages().send(player, "sp-center-set", "%world%", player.getWorld().getName());
            }
            case "info" -> {
                Settings s = plugin.settings();
                String clear = s.clearEnabled() ? "every " + (s.clearIntervalTicks() / 20) + "s" : "off";
                String top = s.clearToBuildHeight() ? "build height" : String.valueOf(s.clearHeight());
                sender.sendMessage(core.colors().render(
                        "<gray>SpawnPlatform world <green>" + s.worldName()
                                + " <gray>floor radius <green>" + s.platformRadius()
                                + " <gray>clear <green>" + clear
                                + " <gray>radius <green>" + s.clearRadius()
                                + " <gray>up to <green>" + top));
            }
            default -> core.messages().send(sender, "sp-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("spawnplatform.admin") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
    }
}
