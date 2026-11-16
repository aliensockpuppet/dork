package io.github.aliensockpuppet.rtpqueue.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RTPQueueApi {
    List<QueueMode> modes();

    Optional<String> queuedMode(UUID player);

    boolean isProcessing(UUID player);

    String bracketName(Player player);

    int waitingInBracket(String modeId, Player player);

    List<String> queuedNames(String modeId);
}
