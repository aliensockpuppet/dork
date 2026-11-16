package io.github.aliensockpuppet.dorkcore.api;

import org.bukkit.command.CommandSender;

public interface Messages {
    void send(CommandSender to, String key, String... pairs);

    boolean has(String key);

    String raw(String key, String fallback);
}
