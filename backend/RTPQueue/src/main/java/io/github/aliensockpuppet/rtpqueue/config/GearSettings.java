package io.github.aliensockpuppet.rtpqueue.config;

public record GearSettings(
        boolean armorBrackets,
        int helmetWeight,
        int chestWeight,
        int legsWeight,
        int bootsWeight,
        double minCoverage,
        boolean scanContainers,
        boolean countEnderChestWhenCarried,
        boolean warnEnderChest,
        boolean explosivesEnabled,
        boolean explosivesNeedBoth,
        int minCrystals,
        boolean crystalsNeedObsidian,
        int minObsidian,
        int minAnchors,
        boolean anchorsNeedGlowstone,
        int minGlowstone,
        boolean ignoreAnchorsInNether,
        boolean bedsCountOutsideOverworld,
        boolean matchElytra,
        int minElytra,
        String nameNetherite,
        String nameDiamond,
        String nameLow,
        String nameOpen,
        String explosivesSuffix,
        String elytraSuffix) {
    public int totalWeight() {
        return helmetWeight + chestWeight + legsWeight + bootsWeight;
    }
}
