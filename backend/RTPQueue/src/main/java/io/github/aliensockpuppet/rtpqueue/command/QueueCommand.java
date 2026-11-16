package io.github.aliensockpuppet.rtpqueue.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.rtpqueue.RTPQueuePlugin;
import io.github.aliensockpuppet.rtpqueue.api.QueueMode;
import io.github.aliensockpuppet.rtpqueue.config.Settings;
import io.github.aliensockpuppet.rtpqueue.queue.QueueManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class QueueCommand implements TabExecutor {
    private final RTPQueuePlugin plugin;

    public QueueCommand(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.core().messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("rtpqueue.use")) {
            plugin.core().messages().send(player, "no-permission");
            plugin.core().sounds().play(player, "error");
            return true;
        }
        Settings s = plugin.settings();
        if (s.modes().isEmpty()) {
            plugin.core().messages().send(player, "queue-disabled");
            return true;
        }
        if (args.length == 0) {
            if (s.modes().size() == 1) {
                handle(player, s.modes().get(0), s);
            } else {
                plugin.core().messages().send(player, "queue-usage", "%modes%", s.modeIds());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            String left = plugin.queue().leave(player.getUniqueId());
            if (left == null) {
                plugin.core().messages().send(player, "queue-not-queued");
            } else {
                String name = s.mode(left).map(QueueMode::name).orElse(left);
                plugin.core().messages().send(player, "queue-left", "%mode%", name);
                plugin.core().sounds().play(player, "queue-leave");
                plugin.queue().announce(player, name, false);
            }
            return true;
        }
        Optional<QueueMode> mode = s.mode(args[0]);
        if (mode.isEmpty()) {
            plugin.core().messages().send(player, "queue-unknown-mode",
                    "%input%", clean(args[0], 24), "%modes%", s.modeIds());
            plugin.core().sounds().play(player, "error");
            return true;
        }
        handle(player, mode.get(), s);
        return true;
    }

    private void handle(Player player, QueueMode mode, Settings s) {
        if (!s.worldAllowed(player.getWorld())) {
            plugin.core().messages().send(player, "world-not-allowed");
            plugin.core().sounds().play(player, "error");
            return;
        }
        QueueManager queue = plugin.queue();
        boolean leaving = queue.queuedMode(player.getUniqueId())
                .map(current -> current.equals(mode.id()))
                .orElse(false);
        if (!leaving && !passesGate(player)) {
            return;
        }
        if (!leaving) {
            String block = queue.modeBlockReason(player, mode);
            if (block != null) {
                plugin.core().messages().send(player, block);
                plugin.core().sounds().play(player, "error");
                return;
            }
        }
        QueueManager.Result result = queue.toggle(player, mode);
        String bracket = queue.profile(player, false).bracketName(s.gear());
        switch (result) {
            case BUSY -> {
                plugin.core().messages().send(player, "queue-busy");
                plugin.core().sounds().play(player, "error");
            }
            case LEFT -> {
                plugin.core().messages().send(player, "queue-left", "%mode%", mode.name());
                plugin.core().sounds().play(player, "queue-leave");
                queue.announce(player, mode.name(), false);
            }
            case JOINED, SWITCHED -> {
                String waiting = String.valueOf(queue.waitingInBracket(mode.id(), player));
                plugin.core().messages().send(player, result == QueueManager.Result.JOINED ? "queue-joined" : "queue-switched",
                        "%mode%", mode.name(), "%bracket%", bracket, "%waiting%", waiting,
                        "%players%", String.valueOf(mode.players()));
                plugin.core().sounds().play(player, "queue-join");
                queue.announce(player, mode.name(), true);
                plugin.scheduleMatchmake();
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (QueueMode m : plugin.settings().modes()) {
            if (m.id().startsWith(prefix)) {
                out.add(m.id());
            }
        }
        if ("leave".startsWith(prefix)) {
            out.add("leave");
        }
        return out;
    }

    private boolean passesGate(Player player) {
        if (!plugin.core().authGate().isAuthenticated(player)) {
            plugin.core().messages().send(player, "not-authenticated");
            plugin.core().sounds().play(player, "error");
            return false;
        }
        if (plugin.core().combatTag().isTagged(player)) {
            long seconds = (plugin.core().combatTag().remainingMillis(player) + 999L) / 1000L;
            plugin.core().messages().send(player, "combat-tagged", "%seconds%", String.valueOf(seconds));
            plugin.core().sounds().play(player, "error");
            return false;
        }
        return true;
    }

    private static String clean(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String out = raw.replace("&", "").replace("§", "").replace("<", "").replace(">", "");
        return out.length() > maxLength ? out.substring(0, maxLength) : out;
    }
}
