package io.github.aliensockpuppet.rtpqueue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import io.github.aliensockpuppet.rtpqueue.api.QueueMode;
import io.github.aliensockpuppet.rtpqueue.api.RTPQueueApi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ApiImpl implements RTPQueueApi {
    private final RTPQueuePlugin plugin;

    ApiImpl(RTPQueuePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<QueueMode> modes() {
        return plugin.settings().modes();
    }

    @Override
    public Optional<String> queuedMode(UUID player) {
        return Bukkit.isPrimaryThread() ? plugin.queue().queuedMode(player) : Optional.empty();
    }

    @Override
    public boolean isProcessing(UUID player) {
        return Bukkit.isPrimaryThread() && plugin.queue().isProcessing(player);
    }

    @Override
    public String bracketName(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            return "";
        }
        return plugin.queue().profile(player, false).bracketName(plugin.settings().gear());
    }

    @Override
    public int waitingInBracket(String modeId, Player player) {
        return Bukkit.isPrimaryThread() ? plugin.queue().waitingInBracket(modeId, player) : 0;
    }

    @Override
    public List<String> queuedNames(String modeId) {
        if (!Bukkit.isPrimaryThread()) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>();
        for (UUID id : plugin.queue().queued(modeId)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                names.add(p.getName());
            }
        }
        return names;
    }
}
