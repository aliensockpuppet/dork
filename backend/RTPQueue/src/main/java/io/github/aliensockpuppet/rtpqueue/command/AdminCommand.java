package io.github.aliensockpuppet.rtpqueue.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.api.QueueMode;
import io.github.aliensockpuppet.rtpqueue.config.Settings;
import io.github.aliensockpuppet.rtpqueue.gear.GearProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AdminCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("reload", "queue", "clear", "inspect");

    private final RTPQueuePlugin plugin;

    public AdminCommand(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rtpqueue.admin")) {
            plugin.core().messages().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.core().messages().send(sender, "admin-reloaded");
            }
            case "clear" -> {
                plugin.queue().clearAll();
                plugin.core().messages().send(sender, "admin-cleared");
            }
            case "queue" -> showQueues(sender);
            case "inspect" -> inspect(sender, args);
            default -> plugin.core().messages().send(sender, "admin-usage");
        }
        return true;
    }

    private void showQueues(CommandSender sender) {
        Settings s = plugin.settings();
        plugin.core().messages().send(sender, "admin-queue-header");
        for (QueueMode mode : s.modes()) {
            List<UUID> ids = plugin.queue().queued(mode.id());
            List<String> names = new ArrayList<>();
            for (UUID id : ids) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    names.add(p.getName() + " (" + plugin.queue().profile(p, false).bracketName(s.gear()) + ")");
                }
            }
            plugin.core().messages().send(sender, "admin-queue-line",
                    "%mode%", mode.name(),
                    "%count%", String.valueOf(ids.size()),
                    "%players%", names.isEmpty() ? "none" : String.join(", ", names));
        }
    }

    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.core().messages().send(sender, "admin-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.core().messages().send(sender, "admin-player-not-found", "%input%", args[1]);
            return;
        }
        Settings s = plugin.settings();
        GearProfile gp = plugin.queue().profile(target, true);
        String queued = plugin.queue().queuedMode(target.getUniqueId())
                .flatMap(s::mode).map(QueueMode::name).orElse("none");
        plugin.core().messages().send(sender, "admin-inspect",
                "%player%", target.getName(),
                "%bracket%", gp.bracketName(s.gear()),
                "%netherite%", String.valueOf(Math.round(gp.netheriteCoverage() * 100)),
                "%diamond%", String.valueOf(Math.round(gp.diamondCoverage() * 100)),
                "%explosives%", gp.explosives() ? "yes" : "no",
                "%echest%", gp.enderChest() ? "yes" : "no",
                "%queue%", queued);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rtpqueue.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
