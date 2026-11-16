package io.github.aliensockpuppet.dorkmotd.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

public final class BackendBridge {
    public static final ChannelIdentifier SET =
            MinecraftChannelIdentifier.from("dorkmotd:set");
    public static final ChannelIdentifier RESULT =
            MinecraftChannelIdentifier.from("dorkmotd:result");

    private static final String RESET_TOKEN = "\u0000reset";

    private final DorkMotdVelocity plugin;
    private final MotdManager motd;

    public BackendBridge(DorkMotdVelocity plugin, MotdManager motd) {
        this.plugin = plugin;
        this.motd = motd;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!SET.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection backend)) {
            return;
        }

        Player player = backend.getPlayer();
        if (player == null) {
            return;
        }

        String rawInput;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            rawInput = in.readUTF();
        } catch (RuntimeException e) {
            return;
        }

        handle(player, backend, rawInput);
    }

    private void handle(Player player, ServerConnection backend, String rawInput) {
        if (RESET_TOKEN.equals(rawInput)) {
            plugin.store().clearMotd();
            motd.rebuildCache();
            reply(backend, "RESET", 0);
            return;
        }

        long now = System.currentTimeMillis();
        MotdManager.Result result = motd.attemptSet(player, rawInput, now);
        switch (result) {
            case OK -> reply(backend, "OK", 0);
            case GLOBAL_COOLDOWN -> reply(backend, "GLOBAL_COOLDOWN", (int) motd.globalCooldownLeft(now));
            case PLAYER_COOLDOWN ->
                    reply(backend, "PLAYER_COOLDOWN", (int) motd.playerCooldownLeft(player.getUniqueId(), now));
            case INVALID -> reply(backend, "INVALID", 0);
            case EMPTY -> reply(backend, "EMPTY", 0);
        }
    }

    private void reply(ServerConnection backend, String code, int seconds) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(code);
        if ("GLOBAL_COOLDOWN".equals(code) || "PLAYER_COOLDOWN".equals(code)) {
            out.writeInt(Math.max(0, seconds));
        }
        backend.sendPluginMessage(RESULT, out.toByteArray());
    }
}
