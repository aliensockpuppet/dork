package io.github.aliensockpuppet.dorkcore.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class CommandThrottle implements TabExecutor {
    private static final long DEFAULT_SECONDS = 1L;
    private static final long FLOOR_MIN_MILLIS = 1_000L;
    private static final long FLOOR_MAX_MILLIS = 2_000L;

    private final CoreServices services;
    private final String key;
    private final CommandExecutor delegate;

    public CommandThrottle(CoreServices services, String commandName, CommandExecutor delegate) {
        this.services = services;
        this.key = "cmd-dorkcore-" + commandName;
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            long left = services.cooldowns().remaining(player.getUniqueId(), key);
            if (left > 0L) {
                services.messages().send(player, "cooldown", "%seconds%", String.valueOf((left + 999L) / 1000L));
                services.sounds().play(player, "error");
                return true;
            }
            long rolled = services.cooldowns().roll(key, DEFAULT_SECONDS);
            long floor = ThreadLocalRandom.current().nextLong(FLOOR_MIN_MILLIS, FLOOR_MAX_MILLIS + 1L);
            services.cooldowns().start(player.getUniqueId(), key, Math.max(rolled, floor));
        }
        return delegate.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (delegate instanceof TabCompleter completer) {
            return completer.onTabComplete(sender, command, alias, args);
        }
        return null;
    }
}
