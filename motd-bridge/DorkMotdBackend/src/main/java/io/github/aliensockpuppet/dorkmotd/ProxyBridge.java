package io.github.aliensockpuppet.dorkmotd;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import io.github.aliensockpuppet.dorkcore.api.DorkCoreApi;
import org.jetbrains.annotations.NotNull;

public final class ProxyBridge implements PluginMessageListener {
    public static final String CHANNEL_SET = "dorkmotd:set";
    public static final String CHANNEL_RESULT = "dorkmotd:result";

    private final DorkMotdPlugin plugin;
    private final DorkCoreApi core;

    public ProxyBridge(DorkMotdPlugin plugin, DorkCoreApi core) {
        this.plugin = plugin;
        this.core = core;
    }

    public boolean sendSet(Player player, String rawInput) {
        if (!player.isOnline()) {
            return false;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(rawInput == null ? "" : rawInput);
        try {
            player.sendPluginMessage(plugin, CHANNEL_SET, out.toByteArray());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL_RESULT.equals(channel)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        final String code;
        try {
            code = in.readUTF();
        } catch (RuntimeException e) {
            return;
        }
        final int seconds = ("GLOBAL_COOLDOWN".equals(code) || "PLAYER_COOLDOWN".equals(code))
                ? readIntSafe(in) : 0;
        final java.util.UUID id = player.getUniqueId();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) {
                return;
            }
            switch (code) {
                case "OK" -> {
                    core.messages().send(p, "motd-set");
                    core.sounds().play(p, "success");
                }
                case "RESET" -> {
                    core.messages().send(p, "motd-reset");
                    core.sounds().play(p, "success");
                }
                case "GLOBAL_COOLDOWN" -> {
                    core.messages().send(p, "motd-global-cooldown", "%seconds%", String.valueOf(seconds));
                    core.sounds().play(p, "error");
                }
                case "PLAYER_COOLDOWN" -> {
                    core.messages().send(p, "motd-player-cooldown", "%seconds%", String.valueOf(seconds));
                    core.sounds().play(p, "error");
                }
                case "INVALID" -> {
                    core.messages().send(p, "motd-invalid");
                    core.sounds().play(p, "error");
                }
                case "EMPTY" -> {
                    core.messages().send(p, "motd-empty");
                    core.sounds().play(p, "error");
                }
                default -> {
                }
            }
        });
    }

    private static int readIntSafe(ByteArrayDataInput in) {
        try {
            return in.readInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
