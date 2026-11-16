package io.github.aliensockpuppet.dorkrestart;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.List;
import java.util.Locale;

public final class RestartCommand implements TabExecutor {
    private final DorkRestartPlugin plugin;
    private final DorkCoreApi core;

    public RestartCommand(DorkRestartPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dorkrestart.admin")) {
            core.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadFromDisk();
            core.messages().send(sender, "restart-reloaded");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("now")) {
            int seconds = 60;
            if (args.length >= 2) {
                try {
                    seconds = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    core.messages().send(sender, "restart-usage");
                    return true;
                }
            }
            plugin.scheduler().restartNow(seconds);
            core.messages().send(sender, "restart-scheduled", "%seconds%", String.valueOf(seconds));
            return true;
        }
        core.messages().send(sender, "restart-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("dorkrestart.admin")) {
            List<String> out = new java.util.ArrayList<>();
            for (String sub : List.of("now", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        return List.of();
    }
}
