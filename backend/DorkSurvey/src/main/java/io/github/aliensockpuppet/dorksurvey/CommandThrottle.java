package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;

import java.util.concurrent.ThreadLocalRandom;

public final class CommandThrottle implements CommandExecutor {
    private static final long DEFAULT_SECONDS = 1L;
    private static final long FLOOR_MIN_MILLIS = 1_000L;
    private static final long FLOOR_MAX_MILLIS = 2_000L;

    private final DorkCoreApi core;
    private final String key;
    private final CommandExecutor delegate;

    public CommandThrottle(DorkCoreApi core, String plugin, String command, CommandExecutor delegate) {
        this.core = core;
        this.key = key(plugin, command);
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (blocked(core, sender, key)) {
            return true;
        }
        return delegate.onCommand(sender, command, label, args);
    }

    public static String key(String plugin, String command) {
        return "cmd-" + plugin + "-" + command;
    }

    public static boolean blocked(DorkCoreApi core, CommandSender sender, String key) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        long left = core.cooldowns().remaining(player.getUniqueId(), key);
        if (left > 0L) {
            core.messages().send(player, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
            core.sounds().play(player, "error");
            return true;
        }
        long rolled = core.cooldowns().roll(key, DEFAULT_SECONDS);
        long floor = ThreadLocalRandom.current().nextLong(FLOOR_MIN_MILLIS, FLOOR_MAX_MILLIS + 1L);
        core.cooldowns().start(player.getUniqueId(), key, Math.max(rolled, floor));
        return false;
    }
}
