package io.github.aliensockpuppet.rtpqueue.config;

import org.bukkit.Material;

import java.util.Set;

public record RtpSettings(
        String world,
        int centerX,
        int centerZ,
        int minRadius,
        int maxRadius,
        int maxAttempts,
        int nearbyMin,
        int nearbyMax,
        int nearbyAttempts,
        int nearbyMaxHeightDifference,
        Set<Material> unsafeBlocks,
        Set<String> blockedBiomes) {
}
