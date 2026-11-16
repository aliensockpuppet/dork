package io.github.aliensockpuppet.rtpqueue.gear;

import io.github.aliensockpuppet.rtpqueue.config.GearSettings;

public record GearProfile(
        GearTier tier,
        boolean explosives,
        boolean enderChest,
        double netheriteCoverage,
        double diamondCoverage,
        boolean elytra,
        String bracketKey) {
    public String bracketName(GearSettings g) {
        String base;
        if (!g.armorBrackets()) {
            base = g.nameOpen();
        } else {
            base = switch (tier) {
                case NETHERITE -> g.nameNetherite();
                case DIAMOND -> g.nameDiamond();
                case LOW -> g.nameLow();
            };
        }
        if (explosives) {
            base += g.explosivesSuffix();
        }
        if (elytra) {
            base += g.elytraSuffix();
        }
        return base;
    }
}
