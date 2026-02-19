use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

// --- Vanilla Minecraft block IDs for conflict checking ---
// ~900 vanilla block names sorted for binary search
const VANILLA_BLOCKS: &[&str] = &[
    "acacia_button", "acacia_door", "acacia_fence", "acacia_fence_gate",
    "acacia_hanging_sign", "acacia_leaves", "acacia_log", "acacia_planks",
    "acacia_pressure_plate", "acacia_sapling", "acacia_sign", "acacia_slab",
    "acacia_stairs", "acacia_trapdoor", "acacia_wall_hanging_sign", "acacia_wall_sign",
    "acacia_wood", "activator_rail", "air", "allium", "amethyst_block",
    "amethyst_cluster", "ancient_debris", "andesite", "andesite_slab",
    "andesite_stairs", "andesite_wall", "anvil", "attached_melon_stem",
    "attached_pumpkin_stem", "azalea", "azalea_leaves", "azure_bluet",
    "bamboo", "bamboo_block", "bamboo_button", "bamboo_door", "bamboo_fence",
    "bamboo_fence_gate", "bamboo_hanging_sign", "bamboo_mosaic", "bamboo_mosaic_slab",
    "bamboo_mosaic_stairs", "bamboo_planks", "bamboo_pressure_plate", "bamboo_sapling",
    "bamboo_sign", "bamboo_slab", "bamboo_stairs", "bamboo_trapdoor",
    "bamboo_wall_hanging_sign", "bamboo_wall_sign", "barrel", "barrier",
    "basalt", "beacon", "bedrock", "bee_nest", "beehive", "beetroots",
    "bell", "big_dripleaf", "big_dripleaf_stem", "birch_button", "birch_door",
    "birch_fence", "birch_fence_gate", "birch_hanging_sign", "birch_leaves",
    "birch_log", "birch_planks", "birch_pressure_plate", "birch_sapling",
    "birch_sign", "birch_slab", "birch_stairs", "birch_trapdoor",
    "birch_wall_hanging_sign", "birch_wall_sign", "birch_wood", "black_banner",
    "black_bed", "black_candle", "black_candle_cake", "black_carpet",
    "black_concrete", "black_concrete_powder", "black_glazed_terracotta",
    "black_shulker_box", "black_stained_glass", "black_stained_glass_pane",
    "black_terracotta", "black_wall_banner", "black_wool", "blackstone",
    "blackstone_slab", "blackstone_stairs", "blackstone_wall", "blast_furnace",
    "blue_banner", "blue_bed", "blue_candle", "blue_candle_cake", "blue_carpet",
    "blue_concrete", "blue_concrete_powder", "blue_glazed_terracotta", "blue_ice",
    "blue_orchid", "blue_shulker_box", "blue_stained_glass",
    "blue_stained_glass_pane", "blue_terracotta", "blue_wall_banner", "blue_wool",
    "bone_block", "bookshelf", "brain_coral", "brain_coral_block", "brain_coral_fan",
    "brain_coral_wall_fan", "brewing_stand", "brick_slab", "brick_stairs",
    "brick_wall", "bricks", "brown_banner", "brown_bed", "brown_candle",
    "brown_candle_cake", "brown_carpet", "brown_concrete", "brown_concrete_powder",
    "brown_glazed_terracotta", "brown_mushroom", "brown_mushroom_block",
    "brown_shulker_box", "brown_stained_glass", "brown_stained_glass_pane",
    "brown_terracotta", "brown_wall_banner", "brown_wool", "bubble_column",
    "bubble_coral", "bubble_coral_block", "bubble_coral_fan", "bubble_coral_wall_fan",
    "budding_amethyst", "cactus", "cake", "calcite", "calibrated_sculk_sensor",
    "campfire", "candle", "candle_cake", "carrots", "cartography_table",
    "carved_pumpkin", "cauldron", "cave_air", "cave_vines", "cave_vines_plant",
    "chain", "chain_command_block", "cherry_button", "cherry_door", "cherry_fence",
    "cherry_fence_gate", "cherry_hanging_sign", "cherry_leaves", "cherry_log",
    "cherry_planks", "cherry_pressure_plate", "cherry_sapling", "cherry_sign",
    "cherry_slab", "cherry_stairs", "cherry_trapdoor", "cherry_wall_hanging_sign",
    "cherry_wall_sign", "cherry_wood", "chest", "chipped_anvil", "chiseled_bookshelf",
    "chiseled_copper", "chiseled_deepslate", "chiseled_nether_bricks",
    "chiseled_polished_blackstone", "chiseled_quartz_block", "chiseled_red_sandstone",
    "chiseled_sandstone", "chiseled_stone_bricks", "chiseled_tuff", "chiseled_tuff_bricks",
    "chorus_flower", "chorus_plant", "clay", "coal_block", "coal_ore",
    "coarse_dirt", "cobbled_deepslate", "cobbled_deepslate_slab",
    "cobbled_deepslate_stairs", "cobbled_deepslate_wall", "cobblestone",
    "cobblestone_slab", "cobblestone_stairs", "cobblestone_wall", "cobweb",
    "cocoa", "command_block", "comparator", "composter", "conduit",
    "copper_block", "copper_bulb", "copper_door", "copper_grate", "copper_ore",
    "copper_trapdoor", "cornflower", "cracked_deepslate_bricks",
    "cracked_deepslate_tiles", "cracked_nether_bricks",
    "cracked_polished_blackstone_bricks", "cracked_stone_bricks", "crafter",
    "crafting_table", "creeper_head", "creeper_wall_head", "crimson_button",
    "crimson_door", "crimson_fence", "crimson_fence_gate", "crimson_fungus",
    "crimson_hanging_sign", "crimson_hyphae", "crimson_nylium", "crimson_planks",
    "crimson_pressure_plate", "crimson_roots", "crimson_sign", "crimson_slab",
    "crimson_stairs", "crimson_stem", "crimson_trapdoor", "crimson_wall_hanging_sign",
    "crimson_wall_sign", "crying_obsidian", "cut_copper", "cut_copper_slab",
    "cut_copper_stairs", "cut_red_sandstone", "cut_red_sandstone_slab",
    "cut_sandstone", "cut_sandstone_slab", "cyan_banner", "cyan_bed",
    "cyan_candle", "cyan_candle_cake", "cyan_carpet", "cyan_concrete",
    "cyan_concrete_powder", "cyan_glazed_terracotta", "cyan_shulker_box",
    "cyan_stained_glass", "cyan_stained_glass_pane", "cyan_terracotta",
    "cyan_wall_banner", "cyan_wool", "damaged_anvil", "dandelion",
    "dark_oak_button", "dark_oak_door", "dark_oak_fence", "dark_oak_fence_gate",
    "dark_oak_hanging_sign", "dark_oak_leaves", "dark_oak_log", "dark_oak_planks",
    "dark_oak_pressure_plate", "dark_oak_sapling", "dark_oak_sign", "dark_oak_slab",
    "dark_oak_stairs", "dark_oak_trapdoor", "dark_oak_wall_hanging_sign",
    "dark_oak_wall_sign", "dark_oak_wood", "dark_prismarine", "dark_prismarine_slab",
    "dark_prismarine_stairs", "daylight_detector", "dead_brain_coral",
    "dead_brain_coral_block", "dead_brain_coral_fan", "dead_brain_coral_wall_fan",
    "dead_bubble_coral", "dead_bubble_coral_block", "dead_bubble_coral_fan",
    "dead_bubble_coral_wall_fan", "dead_bush", "dead_fire_coral",
    "dead_fire_coral_block", "dead_fire_coral_fan", "dead_fire_coral_wall_fan",
    "dead_horn_coral", "dead_horn_coral_block", "dead_horn_coral_fan",
    "dead_horn_coral_wall_fan", "dead_tube_coral", "dead_tube_coral_block",
    "dead_tube_coral_fan", "dead_tube_coral_wall_fan", "decorated_pot",
    "deepslate", "deepslate_brick_slab", "deepslate_brick_stairs",
    "deepslate_brick_wall", "deepslate_bricks", "deepslate_coal_ore",
    "deepslate_copper_ore", "deepslate_diamond_ore", "deepslate_emerald_ore",
    "deepslate_gold_ore", "deepslate_iron_ore", "deepslate_lapis_ore",
    "deepslate_redstone_ore", "deepslate_tile_slab", "deepslate_tile_stairs",
    "deepslate_tile_wall", "deepslate_tiles", "detector_rail", "diamond_block",
    "diamond_ore", "diorite", "diorite_slab", "diorite_stairs", "diorite_wall",
    "dirt", "dirt_path", "dispenser", "dragon_egg", "dragon_head",
    "dragon_wall_head", "dried_kelp_block", "dripstone_block", "dropper",
    "emerald_block", "emerald_ore", "enchanting_table", "end_gateway",
    "end_portal", "end_portal_frame", "end_rod", "end_stone",
    "end_stone_brick_slab", "end_stone_brick_stairs", "end_stone_brick_wall",
    "end_stone_bricks", "ender_chest", "exposed_chiseled_copper", "exposed_copper",
    "exposed_copper_bulb", "exposed_copper_door", "exposed_copper_grate",
    "exposed_copper_trapdoor", "exposed_cut_copper", "exposed_cut_copper_slab",
    "exposed_cut_copper_stairs", "farmland", "fern", "fire", "fire_coral",
    "fire_coral_block", "fire_coral_fan", "fire_coral_wall_fan",
    "fletching_table", "flower_pot", "flowering_azalea", "flowering_azalea_leaves",
    "frogspawn", "frosted_ice", "furnace", "gilded_blackstone", "glass",
    "glass_pane", "glow_lichen", "glowstone", "gold_block", "gold_ore",
    "granite", "granite_slab", "granite_stairs", "granite_wall", "grass_block",
    "gravel", "gray_banner", "gray_bed", "gray_candle", "gray_candle_cake",
    "gray_carpet", "gray_concrete", "gray_concrete_powder",
    "gray_glazed_terracotta", "gray_shulker_box", "gray_stained_glass",
    "gray_stained_glass_pane", "gray_terracotta", "gray_wall_banner", "gray_wool",
    "green_banner", "green_bed", "green_candle", "green_candle_cake",
    "green_carpet", "green_concrete", "green_concrete_powder",
    "green_glazed_terracotta", "green_shulker_box", "green_stained_glass",
    "green_stained_glass_pane", "green_terracotta", "green_wall_banner",
    "green_wool", "grindstone", "hanging_roots", "hay_block",
    "heavy_weighted_pressure_plate", "honey_block", "honeycomb_block", "hopper",
    "horn_coral", "horn_coral_block", "horn_coral_fan", "horn_coral_wall_fan",
    "ice", "infested_chiseled_stone_bricks", "infested_cobblestone",
    "infested_cracked_stone_bricks", "infested_deepslate",
    "infested_mossy_stone_bricks", "infested_stone", "infested_stone_bricks",
    "iron_bars", "iron_block", "iron_door", "iron_ore", "iron_trapdoor",
    "jack_o_lantern", "jigsaw", "jukebox", "jungle_button", "jungle_door",
    "jungle_fence", "jungle_fence_gate", "jungle_hanging_sign", "jungle_leaves",
    "jungle_log", "jungle_planks", "jungle_pressure_plate", "jungle_sapling",
    "jungle_sign", "jungle_slab", "jungle_stairs", "jungle_trapdoor",
    "jungle_wall_hanging_sign", "jungle_wall_sign", "jungle_wood", "kelp",
    "kelp_plant", "ladder", "lantern", "lapis_block", "lapis_ore",
    "large_amethyst_bud", "large_fern", "lava", "lava_cauldron", "lectern",
    "lever", "light", "light_blue_banner", "light_blue_bed", "light_blue_candle",
    "light_blue_candle_cake", "light_blue_carpet", "light_blue_concrete",
    "light_blue_concrete_powder", "light_blue_glazed_terracotta",
    "light_blue_shulker_box", "light_blue_stained_glass",
    "light_blue_stained_glass_pane", "light_blue_terracotta",
    "light_blue_wall_banner", "light_blue_wool", "light_gray_banner",
    "light_gray_bed", "light_gray_candle", "light_gray_candle_cake",
    "light_gray_carpet", "light_gray_concrete", "light_gray_concrete_powder",
    "light_gray_glazed_terracotta", "light_gray_shulker_box",
    "light_gray_stained_glass", "light_gray_stained_glass_pane",
    "light_gray_terracotta", "light_gray_wall_banner", "light_gray_wool",
    "light_weighted_pressure_plate", "lightning_rod", "lilac", "lily_of_the_valley",
    "lily_pad", "lime_banner", "lime_bed", "lime_candle", "lime_candle_cake",
    "lime_carpet", "lime_concrete", "lime_concrete_powder",
    "lime_glazed_terracotta", "lime_shulker_box", "lime_stained_glass",
    "lime_stained_glass_pane", "lime_terracotta", "lime_wall_banner", "lime_wool",
    "lodestone", "loom", "magenta_banner", "magenta_bed", "magenta_candle",
    "magenta_candle_cake", "magenta_carpet", "magenta_concrete",
    "magenta_concrete_powder", "magenta_glazed_terracotta", "magenta_shulker_box",
    "magenta_stained_glass", "magenta_stained_glass_pane", "magenta_terracotta",
    "magenta_wall_banner", "magenta_wool", "magma_block", "mangrove_button",
    "mangrove_door", "mangrove_fence", "mangrove_fence_gate",
    "mangrove_hanging_sign", "mangrove_leaves", "mangrove_log", "mangrove_planks",
    "mangrove_pressure_plate", "mangrove_propagule", "mangrove_roots",
    "mangrove_sign", "mangrove_slab", "mangrove_stairs", "mangrove_trapdoor",
    "mangrove_wall_hanging_sign", "mangrove_wall_sign", "mangrove_wood",
    "medium_amethyst_bud", "melon", "melon_stem", "moss_block", "moss_carpet",
    "mossy_cobblestone", "mossy_cobblestone_slab", "mossy_cobblestone_stairs",
    "mossy_cobblestone_wall", "mossy_stone_brick_slab", "mossy_stone_brick_stairs",
    "mossy_stone_brick_wall", "mossy_stone_bricks", "moving_piston", "mud",
    "mud_brick_slab", "mud_brick_stairs", "mud_brick_wall", "mud_bricks",
    "muddy_mangrove_roots", "mushroom_stem", "mycelium", "nether_brick_fence",
    "nether_brick_slab", "nether_brick_stairs", "nether_brick_wall",
    "nether_bricks", "nether_gold_ore", "nether_portal", "nether_quartz_ore",
    "nether_sprouts", "nether_wart", "nether_wart_block", "netherite_block",
    "netherrack", "note_block", "oak_button", "oak_door", "oak_fence",
    "oak_fence_gate", "oak_hanging_sign", "oak_leaves", "oak_log", "oak_planks",
    "oak_pressure_plate", "oak_sapling", "oak_sign", "oak_slab", "oak_stairs",
    "oak_trapdoor", "oak_wall_hanging_sign", "oak_wall_sign", "oak_wood",
    "observer", "obsidian", "ochre_froglight", "orange_banner", "orange_bed",
    "orange_candle", "orange_candle_cake", "orange_carpet", "orange_concrete",
    "orange_concrete_powder", "orange_glazed_terracotta", "orange_shulker_box",
    "orange_stained_glass", "orange_stained_glass_pane", "orange_terracotta",
    "orange_tulip", "orange_wall_banner", "orange_wool", "oxeye_daisy",
    "oxidized_chiseled_copper", "oxidized_copper", "oxidized_copper_bulb",
    "oxidized_copper_door", "oxidized_copper_grate", "oxidized_copper_trapdoor",
    "oxidized_cut_copper", "oxidized_cut_copper_slab", "oxidized_cut_copper_stairs",
    "packed_ice", "packed_mud", "pearlescent_froglight", "peony",
    "petrified_oak_slab", "piglin_head", "piglin_wall_head", "pink_banner",
    "pink_bed", "pink_candle", "pink_candle_cake", "pink_carpet",
    "pink_concrete", "pink_concrete_powder", "pink_glazed_terracotta",
    "pink_petals", "pink_shulker_box", "pink_stained_glass",
    "pink_stained_glass_pane", "pink_terracotta", "pink_tulip",
    "pink_wall_banner", "pink_wool", "piston", "piston_head",
    "pitcher_crop", "pitcher_plant", "player_head", "player_wall_head",
    "podzol", "pointed_dripstone", "polished_andesite", "polished_andesite_slab",
    "polished_andesite_stairs", "polished_basalt",
    "polished_blackstone", "polished_blackstone_brick_slab",
    "polished_blackstone_brick_stairs", "polished_blackstone_brick_wall",
    "polished_blackstone_bricks", "polished_blackstone_button",
    "polished_blackstone_pressure_plate", "polished_blackstone_slab",
    "polished_blackstone_stairs", "polished_blackstone_wall",
    "polished_deepslate", "polished_deepslate_slab", "polished_deepslate_stairs",
    "polished_deepslate_wall", "polished_diorite", "polished_diorite_slab",
    "polished_diorite_stairs", "polished_granite", "polished_granite_slab",
    "polished_granite_stairs", "polished_tuff", "polished_tuff_slab",
    "polished_tuff_stairs", "polished_tuff_wall", "poppy", "potatoes",
    "potted_acacia_sapling", "potted_allium", "potted_azalea_bush",
    "potted_azure_bluet", "potted_bamboo", "potted_birch_sapling",
    "potted_blue_orchid", "potted_brown_mushroom", "potted_cactus",
    "potted_cherry_sapling", "potted_cornflower", "potted_crimson_fungus",
    "potted_crimson_roots", "potted_dandelion", "potted_dark_oak_sapling",
    "potted_dead_bush", "potted_fern", "potted_flowering_azalea_bush",
    "potted_jungle_sapling", "potted_lily_of_the_valley", "potted_mangrove_propagule",
    "potted_oak_sapling", "potted_orange_tulip", "potted_oxeye_daisy",
    "potted_pink_tulip", "potted_poppy", "potted_red_mushroom", "potted_red_tulip",
    "potted_spruce_sapling", "potted_torchflower", "potted_warped_fungus",
    "potted_warped_roots", "potted_white_tulip", "potted_wither_rose",
    "powder_snow", "powder_snow_cauldron", "powered_rail", "prismarine",
    "prismarine_brick_slab", "prismarine_brick_stairs", "prismarine_bricks",
    "prismarine_slab", "prismarine_stairs", "prismarine_wall", "pumpkin",
    "pumpkin_stem", "purple_banner", "purple_bed", "purple_candle",
    "purple_candle_cake", "purple_carpet", "purple_concrete",
    "purple_concrete_powder", "purple_glazed_terracotta", "purple_shulker_box",
    "purple_stained_glass", "purple_stained_glass_pane", "purple_terracotta",
    "purple_wall_banner", "purple_wool", "purpur_block", "purpur_pillar",
    "purpur_slab", "purpur_stairs", "quartz_block", "quartz_bricks",
    "quartz_pillar", "quartz_slab", "quartz_stairs", "rail", "raw_copper_block",
    "raw_gold_block", "raw_iron_block", "red_banner", "red_bed", "red_candle",
    "red_candle_cake", "red_carpet", "red_concrete", "red_concrete_powder",
    "red_glazed_terracotta", "red_mushroom", "red_mushroom_block",
    "red_nether_brick_slab", "red_nether_brick_stairs", "red_nether_brick_wall",
    "red_nether_bricks", "red_sand", "red_sandstone", "red_sandstone_slab",
    "red_sandstone_stairs", "red_sandstone_wall", "red_shulker_box",
    "red_stained_glass", "red_stained_glass_pane", "red_terracotta",
    "red_tulip", "red_wall_banner", "red_wool", "redstone_block",
    "redstone_lamp", "redstone_ore", "redstone_torch", "redstone_wall_torch",
    "redstone_wire", "reinforced_deepslate", "repeater", "repeating_command_block",
    "respawn_anchor", "rooted_dirt", "rose_bush", "sand", "sandstone",
    "sandstone_slab", "sandstone_stairs", "sandstone_wall", "scaffolding",
    "sculk", "sculk_catalyst", "sculk_sensor", "sculk_shrieker", "sculk_vein",
    "sea_lantern", "sea_pickle", "seagrass", "short_grass", "shroomlight",
    "shulker_box", "skeleton_skull", "skeleton_wall_skull", "slime_block",
    "small_amethyst_bud", "small_dripleaf", "smithing_table", "smoker",
    "smooth_basalt", "smooth_quartz", "smooth_quartz_slab", "smooth_quartz_stairs",
    "smooth_red_sandstone", "smooth_red_sandstone_slab",
    "smooth_red_sandstone_stairs", "smooth_sandstone", "smooth_sandstone_slab",
    "smooth_sandstone_stairs", "smooth_stone", "smooth_stone_slab",
    "sniffer_egg", "snow", "snow_block", "soul_campfire", "soul_fire",
    "soul_lantern", "soul_sand", "soul_soil", "soul_torch", "soul_wall_torch",
    "spawner", "sponge", "spore_blossom", "spruce_button", "spruce_door",
    "spruce_fence", "spruce_fence_gate", "spruce_hanging_sign", "spruce_leaves",
    "spruce_log", "spruce_planks", "spruce_pressure_plate", "spruce_sapling",
    "spruce_sign", "spruce_slab", "spruce_stairs", "spruce_trapdoor",
    "spruce_wall_hanging_sign", "spruce_wall_sign", "spruce_wood",
    "sticky_piston", "stone", "stone_brick_slab", "stone_brick_stairs",
    "stone_brick_wall", "stone_bricks", "stone_button", "stone_pressure_plate",
    "stone_slab", "stone_stairs", "stonecutter", "stripped_acacia_log",
    "stripped_acacia_wood", "stripped_bamboo_block", "stripped_birch_log",
    "stripped_birch_wood", "stripped_cherry_log", "stripped_cherry_wood",
    "stripped_crimson_hyphae", "stripped_crimson_stem", "stripped_dark_oak_log",
    "stripped_dark_oak_wood", "stripped_jungle_log", "stripped_jungle_wood",
    "stripped_mangrove_log", "stripped_mangrove_wood", "stripped_oak_log",
    "stripped_oak_wood", "stripped_spruce_log", "stripped_spruce_wood",
    "stripped_warped_hyphae", "stripped_warped_stem", "structure_block",
    "structure_void", "sugar_cane", "sunflower", "suspicious_gravel",
    "suspicious_sand", "sweet_berry_bush", "tall_grass", "tall_seagrass",
    "target", "terracotta", "tinted_glass", "tnt", "torch", "torchflower",
    "torchflower_crop", "trapped_chest", "trial_spawner", "tripwire",
    "tripwire_hook", "tube_coral", "tube_coral_block", "tube_coral_fan",
    "tube_coral_wall_fan", "tuff", "tuff_brick_slab", "tuff_brick_stairs",
    "tuff_brick_wall", "tuff_bricks", "tuff_slab", "tuff_stairs", "tuff_wall",
    "turtle_egg", "twisting_vines", "twisting_vines_plant", "vault",
    "verdant_froglight", "vine", "void_air", "wall_torch",
    "warped_button", "warped_door", "warped_fence", "warped_fence_gate",
    "warped_fungus", "warped_hanging_sign", "warped_hyphae", "warped_nylium",
    "warped_planks", "warped_pressure_plate", "warped_roots", "warped_sign",
    "warped_slab", "warped_stairs", "warped_stem", "warped_trapdoor",
    "warped_wall_hanging_sign", "warped_wall_sign", "warped_wart_block",
    "water", "water_cauldron", "waxed_chiseled_copper", "waxed_copper_block",
    "waxed_copper_bulb", "waxed_copper_door", "waxed_copper_grate",
    "waxed_copper_trapdoor", "waxed_cut_copper", "waxed_cut_copper_slab",
    "waxed_cut_copper_stairs", "waxed_exposed_chiseled_copper",
    "waxed_exposed_copper", "waxed_exposed_copper_bulb",
    "waxed_exposed_copper_door", "waxed_exposed_copper_grate",
    "waxed_exposed_copper_trapdoor", "waxed_exposed_cut_copper",
    "waxed_exposed_cut_copper_slab", "waxed_exposed_cut_copper_stairs",
    "waxed_oxidized_chiseled_copper", "waxed_oxidized_copper",
    "waxed_oxidized_copper_bulb", "waxed_oxidized_copper_door",
    "waxed_oxidized_copper_grate", "waxed_oxidized_copper_trapdoor",
    "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_slab",
    "waxed_oxidized_cut_copper_stairs", "waxed_weathered_chiseled_copper",
    "waxed_weathered_copper", "waxed_weathered_copper_bulb",
    "waxed_weathered_copper_door", "waxed_weathered_copper_grate",
    "waxed_weathered_copper_trapdoor", "waxed_weathered_cut_copper",
    "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper_stairs",
    "weathered_chiseled_copper", "weathered_copper", "weathered_copper_bulb",
    "weathered_copper_door", "weathered_copper_grate", "weathered_copper_trapdoor",
    "weathered_cut_copper", "weathered_cut_copper_slab",
    "weathered_cut_copper_stairs", "weeping_vines", "weeping_vines_plant",
    "wet_sponge", "wheat", "white_banner", "white_bed", "white_candle",
    "white_candle_cake", "white_carpet", "white_concrete",
    "white_concrete_powder", "white_glazed_terracotta", "white_shulker_box",
    "white_stained_glass", "white_stained_glass_pane", "white_terracotta",
    "white_tulip", "white_wall_banner", "white_wool", "wither_rose",
    "wither_skeleton_skull", "wither_skeleton_wall_skull", "yellow_banner",
    "yellow_bed", "yellow_candle", "yellow_candle_cake", "yellow_carpet",
    "yellow_concrete", "yellow_concrete_powder", "yellow_glazed_terracotta",
    "yellow_shulker_box", "yellow_stained_glass", "yellow_stained_glass_pane",
    "yellow_terracotta", "yellow_wall_banner", "yellow_wool",
];

// --- Request/Response types ---

#[derive(Debug, Serialize)]
pub struct NameValidation {
    pub valid: bool,
    pub conflict: bool,
    pub suggestion: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct CreatedFile {
    pub path: String,
    pub file_type: String,
}

#[derive(Debug, Serialize)]
pub struct BlockCreateResult {
    pub block_json_path: String,
    pub created_files: Vec<CreatedFile>,
}

#[derive(Debug, Serialize)]
pub struct BlockGenerateResult {
    pub created_files: Vec<CreatedFile>,
    pub block_class_path: String,
    pub registration_snippet: String,
}

#[derive(Debug, Serialize)]
pub struct BlockValidationIssue {
    pub severity: String, // "error" | "warning"
    pub message: String,
    pub suggestion: Option<String>,
}

// --- Block JSON types ---

#[derive(Debug, Deserialize, Serialize)]
struct BlockTextures {
    all: Option<String>,
    top: Option<String>,
    bottom: Option<String>,
    north: Option<String>,
    south: Option<String>,
    east: Option<String>,
    west: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
struct BlockProps {
    hardness: f64,
    resistance: f64,
    requires_tool: bool,
    tool_type: String,
    tool_level: u32,
    light_level: u32,
    is_transparent: bool,
    has_gravity: bool,
    flammable: bool,
    slipperiness: f64,
}

#[derive(Debug, Deserialize, Serialize)]
struct BlockProject {
    name: String,
    display_name: String,
    mod_id: String,
    texture_mode: String,
    textures: BlockTextures,
    properties: BlockProps,
    has_gui: bool,
    gui_file: Option<String>,
    has_block_entity: bool,
    custom_code: Option<String>,
    code_overrides: serde_json::Value,
}

// --- Tauri commands ---

#[tauri::command]
pub async fn validate_block_name(name: String) -> Result<NameValidation, String> {
    // Check naming conventions: lowercase, [a-z0-9_] only
    let valid_pattern = name.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_');
    let not_empty = !name.is_empty();
    let not_starts_with_number = !name.starts_with(|c: char| c.is_ascii_digit());
    let valid = valid_pattern && not_empty && not_starts_with_number;

    // Check for vanilla conflict using binary search
    let conflict = VANILLA_BLOCKS.binary_search(&name.as_str()).is_ok();

    let suggestion = if conflict {
        Some(format!("custom_{}", name))
    } else if !valid && not_empty {
        // Suggest a sanitized version
        let sanitized: String = name
            .to_lowercase()
            .chars()
            .map(|c| if c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' { c } else { '_' })
            .collect();
        let sanitized = sanitized.trim_start_matches(|c: char| c.is_ascii_digit()).to_string();
        if sanitized.is_empty() { None } else { Some(sanitized) }
    } else {
        None
    };

    Ok(NameValidation {
        valid,
        conflict,
        suggestion,
    })
}

#[tauri::command]
pub async fn create_block_from_wizard(
    project_path: String,
    block_name: String,
    display_name: String,
    mod_id: String,
    texture_mode: String,
    textures: serde_json::Value,
    properties: serde_json::Value,
    has_gui: bool,
) -> Result<BlockCreateResult, String> {
    let project = Path::new(&project_path);
    if !project.exists() {
        return Err("Project directory does not exist".to_string());
    }

    let mut created_files = Vec::new();

    // Build the block project JSON
    let block_textures: BlockTextures = serde_json::from_value(textures)
        .map_err(|e| format!("Invalid textures: {}", e))?;
    let block_props: BlockProps = serde_json::from_value(properties)
        .map_err(|e| format!("Invalid properties: {}", e))?;

    let gui_file = if has_gui {
        Some(format!("{}.gui.json", block_name))
    } else {
        None
    };

    let block_project = BlockProject {
        name: block_name.clone(),
        display_name: display_name.clone(),
        mod_id: mod_id.clone(),
        texture_mode: texture_mode.clone(),
        textures: block_textures,
        properties: block_props,
        has_gui,
        gui_file: gui_file.clone(),
        has_block_entity: has_gui, // Block entity needed if GUI is present
        custom_code: None,
        code_overrides: serde_json::json!({}),
    };

    // Write .block.json
    let block_json = serde_json::to_string_pretty(&block_project)
        .map_err(|e| format!("Failed to serialize block: {}", e))?;

    let block_json_path = project.join(format!("{}.block.json", block_name));
    fs::write(&block_json_path, &block_json)
        .map_err(|e| format!("Failed to write block JSON: {}", e))?;

    created_files.push(CreatedFile {
        path: block_json_path.to_string_lossy().to_string(),
        file_type: "block_json".to_string(),
    });

    // If has_gui, create the linked .gui.json with defaults
    if has_gui {
        if let Some(ref gui_name) = gui_file {
            let gui_path = project.join(gui_name);
            let gui_content = serde_json::json!({
                "name": block_name,
                "width": 176,
                "height": 166,
                "background_texture": null,
                "elements": []
            });
            fs::write(&gui_path, serde_json::to_string_pretty(&gui_content).unwrap())
                .map_err(|e| format!("Failed to write GUI file: {}", e))?;

            created_files.push(CreatedFile {
                path: gui_path.to_string_lossy().to_string(),
                file_type: "gui_json".to_string(),
            });
        }
    }

    Ok(BlockCreateResult {
        block_json_path: block_json_path.to_string_lossy().to_string(),
        created_files,
    })
}

#[tauri::command]
pub async fn generate_block_code(
    project_path: String,
    block_json_path: String,
) -> Result<BlockGenerateResult, String> {
    let project = Path::new(&project_path);
    let block_path = Path::new(&block_json_path);

    if !block_path.exists() {
        return Err("Block JSON file does not exist".to_string());
    }

    let content = fs::read_to_string(block_path)
        .map_err(|e| format!("Failed to read block JSON: {}", e))?;

    let block: BlockProject = serde_json::from_str(&content)
        .map_err(|e| format!("Failed to parse block JSON: {}", e))?;

    let class_name = to_pascal_case(&block.name);
    let mut created_files = Vec::new();

    // Determine package from alloy.mod.json or fallback
    let package_name = format!("com.{}", block.mod_id);

    // 1. Generate Block Java class
    let java_code = generate_block_class(&block, &class_name, &package_name);
    let java_dir = project
        .join("src/main/java")
        .join(package_name.replace('.', "/"))
        .join("block");

    fs::create_dir_all(&java_dir)
        .map_err(|e| format!("Failed to create directory: {}", e))?;

    let java_path = java_dir.join(format!("{}.java", class_name));
    fs::write(&java_path, &java_code)
        .map_err(|e| format!("Failed to write Java file: {}", e))?;

    created_files.push(CreatedFile {
        path: java_path.to_string_lossy().to_string(),
        file_type: "java_class".to_string(),
    });

    // 2. Generate Block model JSON
    let models_dir = project
        .join("src/main/resources/assets")
        .join(&block.mod_id)
        .join("models/block");
    fs::create_dir_all(&models_dir)
        .map_err(|e| format!("Failed to create models directory: {}", e))?;

    let model_json = generate_block_model(&block);
    let model_path = models_dir.join(format!("{}.json", block.name));
    fs::write(&model_path, &model_json)
        .map_err(|e| format!("Failed to write block model: {}", e))?;

    created_files.push(CreatedFile {
        path: model_path.to_string_lossy().to_string(),
        file_type: "block_model".to_string(),
    });

    // 3. Generate Blockstate JSON
    let blockstates_dir = project
        .join("src/main/resources/assets")
        .join(&block.mod_id)
        .join("blockstates");
    fs::create_dir_all(&blockstates_dir)
        .map_err(|e| format!("Failed to create blockstates directory: {}", e))?;

    let blockstate_json = format!(
        r#"{{
  "variants": {{
    "": {{ "model": "{}:block/{}" }}
  }}
}}"#,
        block.mod_id, block.name
    );
    let blockstate_path = blockstates_dir.join(format!("{}.json", block.name));
    fs::write(&blockstate_path, &blockstate_json)
        .map_err(|e| format!("Failed to write blockstate: {}", e))?;

    created_files.push(CreatedFile {
        path: blockstate_path.to_string_lossy().to_string(),
        file_type: "blockstate".to_string(),
    });

    // 4. Generate Item model JSON
    let item_models_dir = project
        .join("src/main/resources/assets")
        .join(&block.mod_id)
        .join("models/item");
    fs::create_dir_all(&item_models_dir)
        .map_err(|e| format!("Failed to create item models directory: {}", e))?;

    let item_model_json = format!(
        r#"{{
  "parent": "{}:block/{}"
}}"#,
        block.mod_id, block.name
    );
    let item_model_path = item_models_dir.join(format!("{}.json", block.name));
    fs::write(&item_model_path, &item_model_json)
        .map_err(|e| format!("Failed to write item model: {}", e))?;

    created_files.push(CreatedFile {
        path: item_model_path.to_string_lossy().to_string(),
        file_type: "item_model".to_string(),
    });

    // 5. Generate BlockEntity class if needed
    if block.has_block_entity {
        let be_code = generate_block_entity_class(&block, &class_name, &package_name);
        let be_path = java_dir.join(format!("{}BlockEntity.java", class_name));
        fs::write(&be_path, &be_code)
            .map_err(|e| format!("Failed to write BlockEntity: {}", e))?;

        created_files.push(CreatedFile {
            path: be_path.to_string_lossy().to_string(),
            file_type: "java_class".to_string(),
        });
    }

    // 6. Registration snippet
    let upper = block.name.to_uppercase();
    let registration_snippet = format!(
        r#"// Register block: {class_name}
public static final Block {upper} = Registry.register(
    Blocks.class,
    "{name}",
    new {class_name}()
);

// Register block item
public static final Item {upper}_ITEM = Registry.register(
    Items.class,
    "{name}",
    new BlockItem({upper}, new ItemProperties())
);"#,
        class_name = class_name,
        upper = upper,
        name = block.name,
    );

    Ok(BlockGenerateResult {
        created_files,
        block_class_path: java_path.to_string_lossy().to_string(),
        registration_snippet,
    })
}

#[tauri::command]
pub async fn validate_block(
    project_path: String,
    block_json_path: String,
) -> Result<Vec<BlockValidationIssue>, String> {
    let project = Path::new(&project_path);
    let block_path = Path::new(&block_json_path);

    if !block_path.exists() {
        return Err("Block JSON file does not exist".to_string());
    }

    let content = fs::read_to_string(block_path)
        .map_err(|e| format!("Failed to read block JSON: {}", e))?;

    let block: BlockProject = serde_json::from_str(&content)
        .map_err(|e| format!("Failed to parse block JSON: {}", e))?;

    let mut issues = Vec::new();

    // Check name validity
    let name_valid = !block.name.is_empty()
        && block.name.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_');
    if !name_valid {
        issues.push(BlockValidationIssue {
            severity: "error".to_string(),
            message: "Block name is invalid. Must be lowercase with only a-z, 0-9, and underscores.".to_string(),
            suggestion: None,
        });
    }

    // Check vanilla conflict
    if VANILLA_BLOCKS.binary_search(&block.name.as_str()).is_ok() {
        issues.push(BlockValidationIssue {
            severity: "warning".to_string(),
            message: format!("Block name \"{}\" conflicts with a vanilla Minecraft block.", block.name),
            suggestion: Some(format!("Rename to \"custom_{}\"", block.name)),
        });
    }

    // Check textures
    if block.texture_mode == "all" {
        if block.textures.all.is_none() {
            issues.push(BlockValidationIssue {
                severity: "error".to_string(),
                message: "No texture assigned. Block needs at least one texture.".to_string(),
                suggestion: Some("Assign a texture in the Textures tab.".to_string()),
            });
        }
    } else {
        let faces = [
            ("top", &block.textures.top),
            ("bottom", &block.textures.bottom),
            ("north", &block.textures.north),
            ("south", &block.textures.south),
            ("east", &block.textures.east),
            ("west", &block.textures.west),
        ];
        let missing: Vec<&str> = faces.iter()
            .filter(|(_, tex)| tex.is_none())
            .map(|(name, _)| *name)
            .collect();
        if !missing.is_empty() {
            issues.push(BlockValidationIssue {
                severity: "error".to_string(),
                message: format!("Missing textures for faces: {}", missing.join(", ")),
                suggestion: Some("Assign textures for all 6 faces in per-face mode.".to_string()),
            });
        }
    }

    // Check texture files exist
    let textures_dir = project
        .join("src/main/resources/assets")
        .join(&block.mod_id)
        .join("textures/block");

    let check_texture = |name: &Option<String>| -> Option<BlockValidationIssue> {
        if let Some(tex_name) = name {
            let tex_path = textures_dir.join(format!("{}.png", tex_name));
            if !tex_path.exists() {
                return Some(BlockValidationIssue {
                    severity: "warning".to_string(),
                    message: format!("Texture file not found: {}.png", tex_name),
                    suggestion: Some("Import the texture via the Textures tab.".to_string()),
                });
            }
        }
        None
    };

    if block.texture_mode == "all" {
        if let Some(issue) = check_texture(&block.textures.all) {
            issues.push(issue);
        }
    } else {
        for tex in [&block.textures.top, &block.textures.bottom, &block.textures.north,
                     &block.textures.south, &block.textures.east, &block.textures.west] {
            if let Some(issue) = check_texture(tex) {
                issues.push(issue);
            }
        }
    }

    // Check GUI file exists if referenced
    if block.has_gui {
        if let Some(ref gui_file) = block.gui_file {
            let gui_path = block_path.parent()
                .unwrap_or(Path::new("."))
                .join(gui_file);
            if !gui_path.exists() {
                issues.push(BlockValidationIssue {
                    severity: "error".to_string(),
                    message: format!("Referenced GUI file \"{}\" does not exist.", gui_file),
                    suggestion: Some("Create the GUI file or disable 'Has GUI'.".to_string()),
                });
            }
        } else {
            issues.push(BlockValidationIssue {
                severity: "error".to_string(),
                message: "Block has GUI enabled but no GUI file is specified.".to_string(),
                suggestion: Some("Specify a GUI file path.".to_string()),
            });
        }
    }

    // Check for environment violations (block with GUI in server-only mod)
    let mod_json_path = project.join("alloy.mod.json");
    if mod_json_path.exists() {
        if let Ok(mod_content) = fs::read_to_string(&mod_json_path) {
            if let Ok(mod_json) = serde_json::from_str::<serde_json::Value>(&mod_content) {
                let env = mod_json.get("environment").and_then(|e| e.as_str()).unwrap_or("both");
                if env == "server" && block.has_gui {
                    issues.push(BlockValidationIssue {
                        severity: "error".to_string(),
                        message: "Block has GUI enabled but mod environment is server-only. GUIs require client-side code.".to_string(),
                        suggestion: Some("Change mod environment to 'client' or 'both', or disable GUI.".to_string()),
                    });
                }
            }
        }
    }

    // Check property ranges
    if block.properties.light_level > 15 {
        issues.push(BlockValidationIssue {
            severity: "error".to_string(),
            message: "Light level must be between 0 and 15.".to_string(),
            suggestion: None,
        });
    }

    if block.properties.slipperiness < 0.0 || block.properties.slipperiness > 1.0 {
        issues.push(BlockValidationIssue {
            severity: "warning".to_string(),
            message: "Slipperiness should be between 0.0 and 1.0.".to_string(),
            suggestion: Some("0.6 is the default value for most blocks.".to_string()),
        });
    }

    Ok(issues)
}

// --- Code generation helpers ---

fn generate_block_class(block: &BlockProject, class_name: &str, package_name: &str) -> String {
    let extends = if block.has_block_entity {
        "BlockWithEntity"
    } else {
        "Block"
    };

    let mut code = format!(
        r#"package {package_name}.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockProperties;
import net.alloymc.api.block.BlockState;
import net.alloymc.api.registry.Registry;
"#,
        package_name = package_name,
    );

    if block.has_block_entity {
        code.push_str("import net.alloymc.api.block.BlockWithEntity;\n");
        code.push_str("import net.alloymc.api.block.entity.BlockEntity;\n");
        code.push_str("import net.alloymc.api.util.BlockPos;\n");
    }

    if block.has_gui {
        code.push_str("import net.alloymc.api.player.Player;\n");
        code.push_str("import net.alloymc.api.util.Hand;\n");
        code.push_str("import net.alloymc.api.util.ActionResult;\n");
        code.push_str("import net.alloymc.api.util.HitResult;\n");
    }

    code.push_str(&format!(
        r#"
/**
 * {display_name}
 * Generated by Alloy IDE Block Editor
 */
public class {class_name} extends {extends} {{

    public {class_name}() {{
        super(BlockProperties.of()
            .strength({hardness}f, {resistance}f)
"#,
        display_name = block.display_name,
        class_name = class_name,
        extends = extends,
        hardness = block.properties.hardness,
        resistance = block.properties.resistance,
    ));

    if block.properties.requires_tool {
        code.push_str("            .requiresTool()\n");
    }

    if block.properties.light_level > 0 {
        code.push_str(&format!(
            "            .luminance(state -> {})\n",
            block.properties.light_level
        ));
    }

    if block.properties.is_transparent {
        code.push_str("            .nonOpaque()\n");
    }

    if block.properties.has_gravity {
        code.push_str("            .gravity()\n");
    }

    if (block.properties.slipperiness - 0.6).abs() > 0.001 {
        code.push_str(&format!(
            "            .slipperiness({}f)\n",
            block.properties.slipperiness
        ));
    }

    code.push_str("        );\n    }\n");

    // Block entity creation method
    if block.has_block_entity {
        code.push_str(&format!(
            r#"
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {{
        return new {class_name}BlockEntity(pos, state);
    }}
"#,
            class_name = class_name,
        ));
    }

    // GUI open on use
    if block.has_gui {
        code.push_str(&format!(
            r#"
    @Override
    public ActionResult onUse(BlockState state, Player player, Hand hand, HitResult hit) {{
        if (!player.getWorld().isClient()) {{
            BlockEntity be = player.getWorld().getBlockEntity(hit.getBlockPos());
            if (be instanceof {class_name}BlockEntity) {{
                player.openScreen(({class_name}BlockEntity) be);
            }}
        }}
        return ActionResult.SUCCESS;
    }}
"#,
            class_name = class_name,
        ));
    }

    code.push_str("}\n");
    code
}

fn generate_block_entity_class(block: &BlockProject, class_name: &str, package_name: &str) -> String {
    format!(
        r#"package {package_name}.block;

import net.alloymc.api.block.BlockState;
import net.alloymc.api.block.entity.BlockEntity;
import net.alloymc.api.block.entity.BlockEntityType;
import net.alloymc.api.util.BlockPos;

/**
 * Block entity for {display_name}
 * Generated by Alloy IDE Block Editor
 */
public class {class_name}BlockEntity extends BlockEntity {{

    public {class_name}BlockEntity(BlockPos pos, BlockState state) {{
        super(null, pos, state); // TODO: Register BlockEntityType
    }}

    // TODO: Implement inventory, tick logic, and screen handler
}}
"#,
        package_name = package_name,
        class_name = class_name,
        display_name = block.display_name,
    )
}

fn generate_block_model(block: &BlockProject) -> String {
    if block.texture_mode == "all" {
        let tex = block.textures.all.as_deref().unwrap_or("missing");
        format!(
            r#"{{
  "parent": "minecraft:block/cube_all",
  "textures": {{
    "all": "{}:block/{}"
  }}
}}"#,
            block.mod_id, tex
        )
    } else {
        let top = block.textures.top.as_deref().unwrap_or("missing");
        let bottom = block.textures.bottom.as_deref().unwrap_or("missing");
        let north = block.textures.north.as_deref().unwrap_or("missing");
        let south = block.textures.south.as_deref().unwrap_or("missing");
        let east = block.textures.east.as_deref().unwrap_or("missing");
        let west = block.textures.west.as_deref().unwrap_or("missing");
        format!(
            r#"{{
  "parent": "minecraft:block/cube",
  "textures": {{
    "up": "{}:block/{}",
    "down": "{}:block/{}",
    "north": "{}:block/{}",
    "south": "{}:block/{}",
    "east": "{}:block/{}",
    "west": "{}:block/{}"
  }}
}}"#,
            block.mod_id, top,
            block.mod_id, bottom,
            block.mod_id, north,
            block.mod_id, south,
            block.mod_id, east,
            block.mod_id, west,
        )
    }
}

fn to_pascal_case(s: &str) -> String {
    s.split(|c: char| c == '-' || c == '_' || c == ' ')
        .filter(|w| !w.is_empty())
        .map(|word| {
            let mut chars = word.chars();
            match chars.next() {
                Some(c) => c.to_uppercase().to_string() + &chars.as_str().to_lowercase(),
                None => String::new(),
            }
        })
        .collect()
}
