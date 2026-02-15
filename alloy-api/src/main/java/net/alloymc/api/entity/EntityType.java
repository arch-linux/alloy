package net.alloymc.api.entity;

/**
 * Known entity types in the game.
 * This enum provides stable identifiers that survive Minecraft version changes.
 */
public enum EntityType {

    // Players
    PLAYER(Category.PLAYER, false),

    // Passive mobs
    CHICKEN(Category.PASSIVE, false),
    COW(Category.PASSIVE, false),
    PIG(Category.PASSIVE, false),
    SHEEP(Category.PASSIVE, false),
    HORSE(Category.PASSIVE, false),
    DONKEY(Category.PASSIVE, false),
    MULE(Category.PASSIVE, false),
    LLAMA(Category.PASSIVE, false),
    RABBIT(Category.PASSIVE, false),
    TURTLE(Category.PASSIVE, false),
    PARROT(Category.PASSIVE, false),
    CAT(Category.PASSIVE, false),
    FOX(Category.PASSIVE, false),
    MOOSHROOM(Category.PASSIVE, false),
    VILLAGER(Category.PASSIVE, false),
    WANDERING_TRADER(Category.PASSIVE, false),
    IRON_GOLEM(Category.PASSIVE, false),
    SNOW_GOLEM(Category.PASSIVE, false),
    ALLAY(Category.PASSIVE, false),
    FROG(Category.PASSIVE, false),
    CAMEL(Category.PASSIVE, false),
    SNIFFER(Category.PASSIVE, false),
    ARMADILLO(Category.PASSIVE, false),
    AXOLOTL(Category.PASSIVE, false),
    BAT(Category.PASSIVE, false),
    BEE(Category.PASSIVE, false),
    COD(Category.PASSIVE, false),
    DOLPHIN(Category.PASSIVE, false),
    GLOW_SQUID(Category.PASSIVE, false),
    GOAT(Category.PASSIVE, false),
    OCELOT(Category.PASSIVE, false),
    PANDA(Category.PASSIVE, false),
    POLAR_BEAR(Category.PASSIVE, false),
    PUFFERFISH(Category.PASSIVE, false),
    SALMON(Category.PASSIVE, false),
    SQUID(Category.PASSIVE, false),
    STRIDER(Category.PASSIVE, false),
    TADPOLE(Category.PASSIVE, false),
    TROPICAL_FISH(Category.PASSIVE, false),

    // Tameable
    WOLF(Category.PASSIVE, false),

    // Hostile mobs
    ZOMBIE(Category.HOSTILE, true),
    SKELETON(Category.HOSTILE, true),
    CREEPER(Category.HOSTILE, true),
    SPIDER(Category.HOSTILE, true),
    CAVE_SPIDER(Category.HOSTILE, true),
    ENDERMAN(Category.HOSTILE, true),
    SLIME(Category.HOSTILE, true),
    MAGMA_CUBE(Category.HOSTILE, true),
    BLAZE(Category.HOSTILE, true),
    GHAST(Category.HOSTILE, true),
    WITHER_SKELETON(Category.HOSTILE, true),
    WITCH(Category.HOSTILE, true),
    GUARDIAN(Category.HOSTILE, true),
    ELDER_GUARDIAN(Category.HOSTILE, true),
    SHULKER(Category.HOSTILE, true),
    PHANTOM(Category.HOSTILE, true),
    DROWNED(Category.HOSTILE, true),
    PILLAGER(Category.HOSTILE, true),
    RAVAGER(Category.HOSTILE, true),
    VINDICATOR(Category.HOSTILE, true),
    EVOKER(Category.HOSTILE, true),
    VEX(Category.HOSTILE, true),
    HOGLIN(Category.HOSTILE, true),
    PIGLIN(Category.HOSTILE, true),
    PIGLIN_BRUTE(Category.HOSTILE, true),
    ZOGLIN(Category.HOSTILE, true),
    WARDEN(Category.HOSTILE, true),
    BREEZE(Category.HOSTILE, true),
    BOGGED(Category.HOSTILE, true),
    SILVERFISH(Category.HOSTILE, true),
    ENDERMITE(Category.HOSTILE, true),
    STRAY(Category.HOSTILE, true),
    HUSK(Category.HOSTILE, true),
    ZOMBIE_VILLAGER(Category.HOSTILE, true),
    WITHER(Category.HOSTILE, true),
    ENDER_DRAGON(Category.HOSTILE, true),

    // Neutral / Semi-hostile
    ZOMBIFIED_PIGLIN(Category.NEUTRAL, true),

    // Projectiles
    ARROW(Category.PROJECTILE, false),
    SPECTRAL_ARROW(Category.PROJECTILE, false),
    TRIDENT(Category.PROJECTILE, false),
    FIREBALL(Category.PROJECTILE, false),
    SMALL_FIREBALL(Category.PROJECTILE, false),
    WITHER_SKULL(Category.PROJECTILE, false),
    WIND_CHARGE(Category.PROJECTILE, false),
    EGG(Category.PROJECTILE, false),
    SNOWBALL(Category.PROJECTILE, false),
    ENDER_PEARL(Category.PROJECTILE, false),
    POTION(Category.PROJECTILE, false),
    EXPERIENCE_BOTTLE(Category.PROJECTILE, false),
    FISHING_BOBBER(Category.PROJECTILE, false),
    FIREWORK_ROCKET(Category.PROJECTILE, false),

    // Vehicles
    MINECART(Category.VEHICLE, false),
    CHEST_MINECART(Category.VEHICLE, false),
    TNT_MINECART(Category.VEHICLE, false),
    HOPPER_MINECART(Category.VEHICLE, false),
    FURNACE_MINECART(Category.VEHICLE, false),
    BOAT(Category.VEHICLE, false),
    CHEST_BOAT(Category.VEHICLE, false),

    // Items & misc
    ITEM(Category.MISC, false),
    EXPERIENCE_ORB(Category.MISC, false),
    ARMOR_STAND(Category.MISC, false),
    ITEM_FRAME(Category.MISC, false),
    GLOW_ITEM_FRAME(Category.MISC, false),
    PAINTING(Category.MISC, false),
    ITEM_DISPLAY(Category.MISC, false),
    BLOCK_DISPLAY(Category.MISC, false),
    TEXT_DISPLAY(Category.MISC, false),
    FALLING_BLOCK(Category.MISC, false),
    TNT(Category.MISC, false),
    AREA_EFFECT_CLOUD(Category.MISC, false),
    END_CRYSTAL(Category.MISC, false),
    LIGHTNING_BOLT(Category.MISC, false),
    LEASH_KNOT(Category.MISC, false),
    MARKER(Category.MISC, false),

    UNKNOWN(Category.MISC, false);

    private final Category category;
    private final boolean hostile;

    EntityType(Category category, boolean hostile) {
        this.category = category;
        this.hostile = hostile;
    }

    public Category category() { return category; }
    public boolean isHostile() { return hostile; }
    public boolean isPassive() { return category == Category.PASSIVE; }
    public boolean isProjectile() { return category == Category.PROJECTILE; }
    public boolean isVehicle() { return category == Category.VEHICLE; }
    public boolean isPlayer() { return this == PLAYER; }
    public boolean isLiving() { return category == Category.PASSIVE || category == Category.HOSTILE || category == Category.NEUTRAL || category == Category.PLAYER; }

    /**
     * Whether this entity type can be tamed.
     */
    public boolean isTameable() {
        return this == WOLF || this == CAT || this == PARROT || this == HORSE
                || this == DONKEY || this == MULE || this == LLAMA;
    }

    public enum Category {
        PLAYER,
        PASSIVE,
        HOSTILE,
        NEUTRAL,
        PROJECTILE,
        VEHICLE,
        MISC
    }
}
