package io.github.aliensockpuppet.rtpqueue.gear;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import io.github.aliensockpuppet.rtpqueue.config.GearSettings;

public final class GearScanner {
    private static final int HELMET = 0;
    private static final int CHEST = 1;
    private static final int LEGS = 2;
    private static final int BOOTS = 3;

    private static final int ITEM_BUDGET = 4096;

    public GearProfile scan(Player player, GearSettings g, World target) {
        Tally t = new Tally(g.scanContainers());
        PlayerInventory inv = player.getInventory();
        t.addAll(inv.getArmorContents());
        t.addAll(inv.getStorageContents());
        t.addAll(inv.getExtraContents());
        t.add(player.getItemOnCursor(), 0);

        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryType topType = top.getType();
        if (topType == InventoryType.CRAFTING) {
            t.addAll(top.getContents());
        }
        boolean echestOpen = topType == InventoryType.ENDER_CHEST;
        boolean carriesEchest = t.enderChests > 0;
        if (echestOpen || (g.countEnderChestWhenCarried() && carriesEchest)) {
            t.addAll(player.getEnderChest().getContents());
        }

        World.Environment env = target == null ? World.Environment.NORMAL : target.getEnvironment();
        return classify(t.best, t.crystals, t.obsidian, t.anchors, t.glowstone, t.beds, t.elytra, carriesEchest, env, g);
    }

    public static GearProfile classify(int[] bestPerSlot, int crystals, int obsidian, int anchors, int glowstone,
                                       int beds, int elytra, boolean carriesEchest, World.Environment env, GearSettings g) {
        double total = Math.max(1, g.totalWeight());
        double netherite = weightAtLeast(bestPerSlot, 2, g) / total;
        double diamond = weightAtLeast(bestPerSlot, 1, g) / total;
        GearTier tier;
        if (netherite >= g.minCoverage()) {
            tier = GearTier.NETHERITE;
        } else if (diamond >= g.minCoverage()) {
            tier = GearTier.DIAMOND;
        } else {
            tier = GearTier.LOW;
        }

        boolean hasCrystals = crystals >= g.minCrystals()
                && (!g.crystalsNeedObsidian() || obsidian >= g.minObsidian());
        boolean anchorsWork = !(g.ignoreAnchorsInNether() && env == World.Environment.NETHER);
        boolean hasAnchors = anchorsWork && anchors >= g.minAnchors()
                && (!g.anchorsNeedGlowstone() || glowstone >= g.minGlowstone());
        boolean hasBeds = g.bedsCountOutsideOverworld() && env != World.Environment.NORMAL && beds > 0;
        boolean blastKit = hasAnchors || hasBeds;
        boolean explosives = g.explosivesEnabled()
                && (g.explosivesNeedBoth() ? (hasCrystals && blastKit) : (hasCrystals || blastKit));

        boolean hasElytra = g.matchElytra() && elytra >= g.minElytra();

        String key = (g.armorBrackets() ? tier.name() : "OPEN")
                + (explosives ? "_X" : "")
                + (hasElytra ? "_E" : "");
        return new GearProfile(tier, explosives, carriesEchest, netherite, diamond, hasElytra, key);
    }

    private static double weightAtLeast(int[] best, int minTier, GearSettings g) {
        double sum = 0;
        if (best[HELMET] >= minTier) sum += g.helmetWeight();
        if (best[CHEST] >= minTier) sum += g.chestWeight();
        if (best[LEGS] >= minTier) sum += g.legsWeight();
        if (best[BOOTS] >= minTier) sum += g.bootsWeight();
        return sum;
    }

    private static final class Tally {
        private final boolean scanContainers;
        final int[] best = new int[4];
        private int budget = ITEM_BUDGET;
        int crystals;
        int obsidian;
        int anchors;
        int glowstone;
        int beds;
        int elytra;
        int enderChests;

        Tally(boolean scanContainers) {
            this.scanContainers = scanContainers;
        }

        void addAll(ItemStack[] items) {
            if (items == null) {
                return;
            }
            for (ItemStack item : items) {
                add(item, 0);
            }
        }

        void add(ItemStack item, int depth) {
            if (item == null || budget <= 0) {
                return;
            }
            budget--;
            Material type = item.getType();
            if (type.isAir()) {
                return;
            }
            String name = type.name();
            int amount = Math.max(1, item.getAmount());
            int slot = slotOf(name);
            if (slot >= 0) {
                int tier = name.startsWith("NETHERITE_") ? 2 : name.startsWith("DIAMOND_") ? 1 : 0;
                if (tier > best[slot]) {
                    best[slot] = tier;
                }
                return;
            }
            switch (type) {
                case END_CRYSTAL -> crystals += amount;
                case OBSIDIAN -> obsidian += amount;
                case RESPAWN_ANCHOR -> anchors += amount;
                case GLOWSTONE -> glowstone += amount;
                case ELYTRA -> elytra += amount;
                case ENDER_CHEST -> enderChests += amount;
                default -> {
                    if (name.endsWith("_BED")) {
                        beds += amount;
                    }
                }
            }
            if (depth == 0 && scanContainers && item.hasItemMeta()
                    && (name.endsWith("SHULKER_BOX") || name.endsWith("BUNDLE"))) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState()
                        && bsm.getBlockState() instanceof ShulkerBox box) {
                    for (ItemStack inner : box.getInventory().getContents()) {
                        add(inner, 1);
                    }
                } else if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
                    for (ItemStack inner : bundle.getItems()) {
                        add(inner, 1);
                    }
                }
            }
        }

        private static int slotOf(String name) {
            if (name.endsWith("_HELMET")) return HELMET;
            if (name.endsWith("_CHESTPLATE")) return CHEST;
            if (name.endsWith("_LEGGINGS")) return LEGS;
            if (name.endsWith("_BOOTS")) return BOOTS;
            return -1;
        }
    }
}
