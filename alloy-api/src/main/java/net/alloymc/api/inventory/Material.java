package net.alloymc.api.inventory;

/**
 * Materials represent block and item types. This enum provides stable identifiers
 * that survive Minecraft version changes. The Alloy loader maps these to internal
 * Minecraft IDs at runtime.
 */
public enum Material {

    // Air
    AIR(false, false),

    // Stone variants
    STONE(true, true),
    GRANITE(true, true),
    DIORITE(true, true),
    ANDESITE(true, true),
    DEEPSLATE(true, true),
    COBBLESTONE(true, true),
    OBSIDIAN(true, true),
    BEDROCK(true, true),
    NETHERRACK(true, true),
    END_STONE(true, true),

    // Dirt/Grass
    DIRT(true, true),
    GRASS_BLOCK(true, true),
    FARMLAND(true, true),
    SAND(true, true),
    GRAVEL(true, true),
    CLAY(true, true),
    MUD(true, true),
    SOUL_SAND(true, true),
    SOUL_SOIL(true, true),
    MYCELIUM(true, true),
    PODZOL(true, true),
    MOSS_BLOCK(true, true),

    // Wood
    OAK_LOG(true, true),
    OAK_PLANKS(true, true),
    OAK_DOOR(true, false),
    OAK_FENCE(true, true),
    OAK_FENCE_GATE(true, true),
    OAK_TRAPDOOR(true, false),
    OAK_BUTTON(true, false),
    OAK_SIGN(true, false),
    OAK_WALL_SIGN(true, false),
    SPRUCE_DOOR(true, false),
    BIRCH_DOOR(true, false),
    JUNGLE_DOOR(true, false),
    ACACIA_DOOR(true, false),
    DARK_OAK_DOOR(true, false),
    CRIMSON_DOOR(true, false),
    WARPED_DOOR(true, false),
    MANGROVE_DOOR(true, false),
    CHERRY_DOOR(true, false),
    BAMBOO_DOOR(true, false),
    IRON_DOOR(true, false),

    // Containers
    CHEST(true, true),
    TRAPPED_CHEST(true, true),
    BARREL(true, true),
    ENDER_CHEST(true, true),
    SHULKER_BOX(true, true),
    FURNACE(true, true),
    BLAST_FURNACE(true, true),
    SMOKER(true, true),
    HOPPER(true, true),
    DROPPER(true, true),
    DISPENSER(true, true),
    BREWING_STAND(true, true),

    // Redstone
    LEVER(true, false),
    STONE_BUTTON(true, false),
    REDSTONE_WIRE(true, false),
    REPEATER(true, false),
    COMPARATOR(true, false),
    PISTON(true, true),
    STICKY_PISTON(true, true),
    TNT(true, true),
    NOTE_BLOCK(true, true),
    OBSERVER(true, true),
    DAYLIGHT_DETECTOR(true, true),

    // Crafting/Utility
    CRAFTING_TABLE(true, true),
    ANVIL(true, true),
    CHIPPED_ANVIL(true, true),
    DAMAGED_ANVIL(true, true),
    ENCHANTING_TABLE(true, true),
    GRINDSTONE(true, true),
    STONECUTTER(true, true),
    LOOM(true, true),
    CARTOGRAPHY_TABLE(true, true),
    SMITHING_TABLE(true, true),
    FLETCHING_TABLE(true, true),
    BEACON(true, true),
    BELL(true, true),
    LECTERN(true, true),
    COMPOSTER(true, true),
    CAULDRON(true, true),
    JUKEBOX(true, true),
    RESPAWN_ANCHOR(true, true),
    LODESTONE(true, true),
    CONDUIT(true, true),

    // Beds
    WHITE_BED(true, false),
    RED_BED(true, false),
    BLUE_BED(true, false),
    GREEN_BED(true, false),
    BLACK_BED(true, false),

    // Glass
    GLASS(true, true),
    GLASS_PANE(true, true),

    // Ores & Minerals
    IRON_ORE(true, true),
    GOLD_ORE(true, true),
    DIAMOND_ORE(true, true),
    EMERALD_ORE(true, true),
    LAPIS_ORE(true, true),
    REDSTONE_ORE(true, true),
    COAL_ORE(true, true),
    COPPER_ORE(true, true),
    IRON_BLOCK(true, true),
    GOLD_BLOCK(true, true),
    DIAMOND_BLOCK(true, true),
    EMERALD_BLOCK(true, true),
    GLOWSTONE(true, true),

    // Liquids
    WATER(true, false),
    LAVA(true, false),

    // Plants & Crops
    OAK_SAPLING(true, false),
    WHEAT(true, false),
    CARROTS(true, false),
    POTATOES(true, false),
    BEETROOTS(true, false),
    MELON(true, true),
    PUMPKIN(true, true),
    SUGAR_CANE(true, false),
    CACTUS(true, false),
    BAMBOO(true, false),
    LILY_PAD(true, false),
    VINE(true, false),
    SWEET_BERRY_BUSH(true, false),
    CHORUS_FLOWER(true, false),
    CHORUS_PLANT(true, false),
    KELP(true, false),
    SEAGRASS(true, false),
    TALL_GRASS(true, false),

    // Fire / Effects
    FIRE(true, false),
    SOUL_FIRE(true, false),
    CAMPFIRE(true, false),
    SOUL_CAMPFIRE(true, false),
    TORCH(true, false),
    SOUL_TORCH(true, false),
    LANTERN(true, true),
    SOUL_LANTERN(true, true),

    // Misc blocks
    LADDER(true, false),
    SCAFFOLDING(true, false),
    COBWEB(true, false),
    SPAWNER(true, true),
    CAKE(true, false),
    TURTLE_EGG(true, false),
    SNIFFER_EGG(true, false),
    DECORATED_POT(true, false),
    POWDER_SNOW(true, false),
    RAIL(true, false),
    POWERED_RAIL(true, false),
    DETECTOR_RAIL(true, false),
    ACTIVATOR_RAIL(true, false),
    END_PORTAL_FRAME(true, true),

    // Items only (not blocks)
    WOODEN_SWORD(false, false),
    STONE_SWORD(false, false),
    IRON_SWORD(false, false),
    GOLDEN_SWORD(false, false),
    DIAMOND_SWORD(false, false),
    NETHERITE_SWORD(false, false),
    WOODEN_SHOVEL(false, false),
    STONE_SHOVEL(false, false),
    IRON_SHOVEL(false, false),
    GOLDEN_SHOVEL(false, false),
    DIAMOND_SHOVEL(false, false),
    NETHERITE_SHOVEL(false, false),
    WOODEN_PICKAXE(false, false),
    STONE_PICKAXE(false, false),
    IRON_PICKAXE(false, false),
    GOLDEN_PICKAXE(false, false),
    DIAMOND_PICKAXE(false, false),
    NETHERITE_PICKAXE(false, false),
    WOODEN_AXE(false, false),
    STONE_AXE(false, false),
    IRON_AXE(false, false),
    GOLDEN_AXE(false, false),
    DIAMOND_AXE(false, false),
    NETHERITE_AXE(false, false),
    WOODEN_HOE(false, false),
    STONE_HOE(false, false),
    IRON_HOE(false, false),
    GOLDEN_HOE(false, false),
    DIAMOND_HOE(false, false),
    NETHERITE_HOE(false, false),
    BOW(false, false),
    CROSSBOW(false, false),
    TRIDENT(false, false),
    SHIELD(false, false),
    FISHING_ROD(false, false),
    FLINT_AND_STEEL(false, false),
    SHEARS(false, false),
    STICK(false, false),
    BONE(false, false),
    BOOK(false, false),
    WRITTEN_BOOK(false, false),
    WRITABLE_BOOK(false, false),
    NAME_TAG(false, false),
    LEAD(false, false),
    SADDLE(false, false),
    BUCKET(false, false),
    WATER_BUCKET(false, false),
    LAVA_BUCKET(false, false),
    ENDER_PEARL(false, false),
    EGG(false, false),
    SNOWBALL(false, false),
    SPAWN_EGG(false, false),

    UNKNOWN(false, false);

    private final boolean block;
    private final boolean solid;

    Material(boolean block, boolean solid) {
        this.block = block;
        this.solid = solid;
    }

    public boolean isBlock() { return block; }
    public boolean isSolid() { return solid; }
    public boolean isAir() { return this == AIR; }

    public boolean isLiquid() {
        return this == WATER || this == LAVA;
    }

    public boolean isDoor() {
        return name().endsWith("_DOOR");
    }

    public boolean isTrapdoor() {
        return name().endsWith("_TRAPDOOR") || this == OAK_TRAPDOOR;
    }

    public boolean isFenceGate() {
        return name().endsWith("_FENCE_GATE") || this == OAK_FENCE_GATE;
    }

    public boolean isButton() {
        return name().endsWith("_BUTTON") || this == OAK_BUTTON || this == STONE_BUTTON;
    }

    public boolean isBed() {
        return name().endsWith("_BED");
    }

    public boolean isSign() {
        return name().endsWith("_SIGN");
    }

    public boolean isContainer() {
        return this == CHEST || this == TRAPPED_CHEST || this == BARREL
                || this == ENDER_CHEST || this == SHULKER_BOX
                || this == FURNACE || this == BLAST_FURNACE || this == SMOKER
                || this == HOPPER || this == DROPPER || this == DISPENSER
                || this == BREWING_STAND;
    }

    public boolean isInteractable() {
        return isContainer() || isDoor() || isTrapdoor() || isFenceGate()
                || isButton() || isBed() || this == LEVER
                || this == CRAFTING_TABLE || this == ANVIL || this == CHIPPED_ANVIL || this == DAMAGED_ANVIL
                || this == ENCHANTING_TABLE || this == GRINDSTONE || this == STONECUTTER
                || this == LOOM || this == CARTOGRAPHY_TABLE || this == SMITHING_TABLE
                || this == BEACON || this == BELL || this == LECTERN || this == JUKEBOX
                || this == NOTE_BLOCK || this == CAULDRON || this == COMPOSTER
                || this == CAKE || this == RESPAWN_ANCHOR || this == DECORATED_POT;
    }

    public boolean isShovel() {
        return this == WOODEN_SHOVEL || this == STONE_SHOVEL || this == IRON_SHOVEL
                || this == GOLDEN_SHOVEL || this == DIAMOND_SHOVEL || this == NETHERITE_SHOVEL;
    }
}
