package net.alloymc.mod.griefprevention.config;

import net.alloymc.api.inventory.Material;
import net.alloymc.mod.griefprevention.claim.ClaimsMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * All configuration for the GriefPrevention mod.
 * Loaded from a properties file for simplicity and portability.
 */
public class GriefPreventionConfig {

    // ---- Claims ----
    public int claimsInitialBlocks = 100;
    public int claimsBlocksAccruedPerHour = 100;
    public int claimsMaxAccruedBlocks = 80000;
    public double claimsAbandonReturnRatio = 1.0;
    public int claimsAutoClaimRadius = 4;
    public int claimsExtendIntoGround = 5;
    public int claimsMinWidth = 5;
    public int claimsMinArea = 100;
    public int claimsMaxPerPlayer = 0; // 0 = unlimited
    public int claimsExpirationDaysInactive = 60;
    public int claimsExpirationChestDays = 7;
    public int claimsExpirationExemptTotalBlocks = 10000;
    public int claimsExpirationExemptBonusBlocks = 5000;
    public int claimsMinimumY = Integer.MIN_VALUE;

    public boolean claimsPreventTheft = true;
    public boolean claimsProtectCreatures = true;
    public boolean claimsProtectHorses = true;
    public boolean claimsProtectDonkeys = true;
    public boolean claimsProtectLlamas = true;
    public boolean claimsPreventButtonsSwitches = true;
    public boolean claimsLockWoodenDoors = false;
    public boolean claimsLockTrapDoors = false;
    public boolean claimsLockFenceGates = true;
    public boolean claimsEnderPearlsRequireAccessTrust = true;
    public boolean claimsFireSpreads = false;
    public boolean claimsFireDamages = false;
    public boolean claimsRavagersBreakBlocks = true;
    public boolean claimsLecternReadingRequiresAccessTrust = true;
    public boolean claimsVillagerTradingRequiresPermission = true;
    public boolean claimsAllowTrappedInAdminClaims = false;
    public boolean claimsDeliverManuals = true;

    public Material claimsInvestigationTool = Material.STICK;
    public Material claimsModificationTool = Material.GOLDEN_SHOVEL;

    // ---- Per-world claim modes ----
    public final Map<String, ClaimsMode> worldClaimModes = new HashMap<>();

    // ---- PvP ----
    public boolean pvpProtectFreshSpawns = true;
    public boolean pvpPunishLogout = true;
    public int pvpCombatTimeoutSeconds = 15;
    public boolean pvpAllowCombatItemDrop = false;
    public boolean pvpProtectPlayersInPlayerClaims = true;
    public boolean pvpProtectPlayersInAdminClaims = true;
    public boolean pvpProtectPetsOutsideClaims = false;
    public String pvpBlockedCommands = "/home;/vanish;/spawn;/tpa";
    public boolean pvpAllowLavaNearPlayersInPvp = true;
    public boolean pvpAllowLavaNearPlayersInNonPvp = false;
    public boolean pvpAllowFireNearPlayersInPvp = true;
    public boolean pvpAllowFireNearPlayersInNonPvp = false;
    public final Map<String, Boolean> worldPvpRules = new HashMap<>();

    // ---- Death drops ----
    public boolean protectDeathDropsPvp = false;
    public boolean protectDeathDropsNonPvp = true;

    // ---- Explosions ----
    public boolean blockClaimExplosions = true;
    public boolean blockSurfaceCreeperExplosions = true;
    public boolean blockSurfaceOtherExplosions = true;

    // ---- Fire / spread ----
    public boolean fireSpreads = false;
    public boolean fireDestroys = false;

    // ---- Entity ----
    public boolean endermenMoveBlocks = false;
    public boolean silverfishBreakBlocks = false;
    public boolean creaturesTrampleCrops = false;
    public boolean rabbitsEatCrops = true;
    public boolean zombiesBreakDoors = false;
    public boolean mobProjectilesChangeBlocks = false;

    // ---- Trees / pistons ----
    public boolean limitSkyTrees = true;
    public boolean limitTreeGrowth = false;
    public PistonMode pistonMode = PistonMode.CLAIMS_ONLY;

    // ---- Spam ----
    public boolean spamEnabled = true;
    public int spamLoginCooldownSeconds = 60;
    public boolean spamBanOffenders = true;
    public String spamWarningMessage = "Please reduce your chat rate.";

    // ---- Admin ----
    public boolean smartBan = true;
    public int maxPlayersPerIp = 3;

    public void load(Path file) {
        if (!Files.exists(file)) {
            save(file);
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load GriefPrevention config from " + file, e);
        }

        claimsInitialBlocks = intProp(props, "claims.initial-blocks", claimsInitialBlocks);
        claimsBlocksAccruedPerHour = intProp(props, "claims.blocks-accrued-per-hour", claimsBlocksAccruedPerHour);
        claimsMaxAccruedBlocks = intProp(props, "claims.max-accrued-blocks", claimsMaxAccruedBlocks);
        claimsAbandonReturnRatio = doubleProp(props, "claims.abandon-return-ratio", claimsAbandonReturnRatio);
        claimsAutoClaimRadius = intProp(props, "claims.auto-claim-radius", claimsAutoClaimRadius);
        claimsExtendIntoGround = intProp(props, "claims.extend-into-ground", claimsExtendIntoGround);
        claimsMinWidth = intProp(props, "claims.min-width", claimsMinWidth);
        claimsMinArea = intProp(props, "claims.min-area", claimsMinArea);
        claimsMaxPerPlayer = intProp(props, "claims.max-per-player", claimsMaxPerPlayer);
        claimsExpirationDaysInactive = intProp(props, "claims.expiration-days-inactive", claimsExpirationDaysInactive);
        claimsExpirationChestDays = intProp(props, "claims.expiration-chest-days", claimsExpirationChestDays);
        claimsMinimumY = intProp(props, "claims.minimum-y", claimsMinimumY);

        claimsPreventTheft = boolProp(props, "claims.prevent-theft", claimsPreventTheft);
        claimsProtectCreatures = boolProp(props, "claims.protect-creatures", claimsProtectCreatures);
        claimsProtectHorses = boolProp(props, "claims.protect-horses", claimsProtectHorses);
        claimsProtectDonkeys = boolProp(props, "claims.protect-donkeys", claimsProtectDonkeys);
        claimsProtectLlamas = boolProp(props, "claims.protect-llamas", claimsProtectLlamas);
        claimsPreventButtonsSwitches = boolProp(props, "claims.prevent-buttons-switches", claimsPreventButtonsSwitches);
        claimsLockWoodenDoors = boolProp(props, "claims.lock-wooden-doors", claimsLockWoodenDoors);
        claimsLockTrapDoors = boolProp(props, "claims.lock-trap-doors", claimsLockTrapDoors);
        claimsLockFenceGates = boolProp(props, "claims.lock-fence-gates", claimsLockFenceGates);
        claimsEnderPearlsRequireAccessTrust = boolProp(props, "claims.ender-pearls-require-access", claimsEnderPearlsRequireAccessTrust);
        claimsFireSpreads = boolProp(props, "claims.fire-spreads", claimsFireSpreads);
        claimsFireDamages = boolProp(props, "claims.fire-damages", claimsFireDamages);
        claimsRavagersBreakBlocks = boolProp(props, "claims.ravagers-break-blocks", claimsRavagersBreakBlocks);
        claimsLecternReadingRequiresAccessTrust = boolProp(props, "claims.lectern-requires-access", claimsLecternReadingRequiresAccessTrust);

        pvpProtectFreshSpawns = boolProp(props, "pvp.protect-fresh-spawns", pvpProtectFreshSpawns);
        pvpPunishLogout = boolProp(props, "pvp.punish-logout", pvpPunishLogout);
        pvpCombatTimeoutSeconds = intProp(props, "pvp.combat-timeout-seconds", pvpCombatTimeoutSeconds);
        pvpAllowCombatItemDrop = boolProp(props, "pvp.allow-combat-item-drop", pvpAllowCombatItemDrop);
        pvpProtectPlayersInPlayerClaims = boolProp(props, "pvp.protect-in-player-claims", pvpProtectPlayersInPlayerClaims);
        pvpProtectPlayersInAdminClaims = boolProp(props, "pvp.protect-in-admin-claims", pvpProtectPlayersInAdminClaims);
        pvpProtectPetsOutsideClaims = boolProp(props, "pvp.protect-pets-outside-claims", pvpProtectPetsOutsideClaims);
        pvpBlockedCommands = props.getProperty("pvp.blocked-commands", pvpBlockedCommands);

        protectDeathDropsPvp = boolProp(props, "drops.protect-pvp", protectDeathDropsPvp);
        protectDeathDropsNonPvp = boolProp(props, "drops.protect-non-pvp", protectDeathDropsNonPvp);

        blockClaimExplosions = boolProp(props, "explosions.block-in-claims", blockClaimExplosions);
        blockSurfaceCreeperExplosions = boolProp(props, "explosions.block-surface-creeper", blockSurfaceCreeperExplosions);
        blockSurfaceOtherExplosions = boolProp(props, "explosions.block-surface-other", blockSurfaceOtherExplosions);

        fireSpreads = boolProp(props, "fire.spreads", fireSpreads);
        fireDestroys = boolProp(props, "fire.destroys", fireDestroys);

        endermenMoveBlocks = boolProp(props, "entity.endermen-move-blocks", endermenMoveBlocks);
        silverfishBreakBlocks = boolProp(props, "entity.silverfish-break-blocks", silverfishBreakBlocks);
        creaturesTrampleCrops = boolProp(props, "entity.creatures-trample-crops", creaturesTrampleCrops);
        rabbitsEatCrops = boolProp(props, "entity.rabbits-eat-crops", rabbitsEatCrops);
        zombiesBreakDoors = boolProp(props, "entity.zombies-break-doors", zombiesBreakDoors);

        limitSkyTrees = boolProp(props, "trees.limit-sky-trees", limitSkyTrees);
        limitTreeGrowth = boolProp(props, "trees.limit-growth", limitTreeGrowth);

        String pistonStr = props.getProperty("pistons.mode", pistonMode.name());
        try { pistonMode = PistonMode.valueOf(pistonStr.toUpperCase()); } catch (Exception ignored) {}

        spamEnabled = boolProp(props, "spam.enabled", spamEnabled);
        smartBan = boolProp(props, "admin.smart-ban", smartBan);
        maxPlayersPerIp = intProp(props, "admin.max-players-per-ip", maxPlayersPerIp);
    }

    public void save(Path file) {
        Properties props = new Properties();

        props.setProperty("claims.initial-blocks", String.valueOf(claimsInitialBlocks));
        props.setProperty("claims.blocks-accrued-per-hour", String.valueOf(claimsBlocksAccruedPerHour));
        props.setProperty("claims.max-accrued-blocks", String.valueOf(claimsMaxAccruedBlocks));
        props.setProperty("claims.abandon-return-ratio", String.valueOf(claimsAbandonReturnRatio));
        props.setProperty("claims.auto-claim-radius", String.valueOf(claimsAutoClaimRadius));
        props.setProperty("claims.extend-into-ground", String.valueOf(claimsExtendIntoGround));
        props.setProperty("claims.min-width", String.valueOf(claimsMinWidth));
        props.setProperty("claims.min-area", String.valueOf(claimsMinArea));
        props.setProperty("claims.max-per-player", String.valueOf(claimsMaxPerPlayer));
        props.setProperty("claims.expiration-days-inactive", String.valueOf(claimsExpirationDaysInactive));
        props.setProperty("claims.expiration-chest-days", String.valueOf(claimsExpirationChestDays));
        props.setProperty("claims.minimum-y", String.valueOf(claimsMinimumY));

        props.setProperty("claims.prevent-theft", String.valueOf(claimsPreventTheft));
        props.setProperty("claims.protect-creatures", String.valueOf(claimsProtectCreatures));
        props.setProperty("claims.protect-horses", String.valueOf(claimsProtectHorses));
        props.setProperty("claims.protect-donkeys", String.valueOf(claimsProtectDonkeys));
        props.setProperty("claims.protect-llamas", String.valueOf(claimsProtectLlamas));
        props.setProperty("claims.prevent-buttons-switches", String.valueOf(claimsPreventButtonsSwitches));
        props.setProperty("claims.lock-wooden-doors", String.valueOf(claimsLockWoodenDoors));
        props.setProperty("claims.lock-trap-doors", String.valueOf(claimsLockTrapDoors));
        props.setProperty("claims.lock-fence-gates", String.valueOf(claimsLockFenceGates));
        props.setProperty("claims.ender-pearls-require-access", String.valueOf(claimsEnderPearlsRequireAccessTrust));
        props.setProperty("claims.fire-spreads", String.valueOf(claimsFireSpreads));
        props.setProperty("claims.fire-damages", String.valueOf(claimsFireDamages));
        props.setProperty("claims.ravagers-break-blocks", String.valueOf(claimsRavagersBreakBlocks));
        props.setProperty("claims.lectern-requires-access", String.valueOf(claimsLecternReadingRequiresAccessTrust));

        props.setProperty("pvp.protect-fresh-spawns", String.valueOf(pvpProtectFreshSpawns));
        props.setProperty("pvp.punish-logout", String.valueOf(pvpPunishLogout));
        props.setProperty("pvp.combat-timeout-seconds", String.valueOf(pvpCombatTimeoutSeconds));
        props.setProperty("pvp.allow-combat-item-drop", String.valueOf(pvpAllowCombatItemDrop));
        props.setProperty("pvp.protect-in-player-claims", String.valueOf(pvpProtectPlayersInPlayerClaims));
        props.setProperty("pvp.protect-in-admin-claims", String.valueOf(pvpProtectPlayersInAdminClaims));
        props.setProperty("pvp.protect-pets-outside-claims", String.valueOf(pvpProtectPetsOutsideClaims));
        props.setProperty("pvp.blocked-commands", pvpBlockedCommands);

        props.setProperty("drops.protect-pvp", String.valueOf(protectDeathDropsPvp));
        props.setProperty("drops.protect-non-pvp", String.valueOf(protectDeathDropsNonPvp));

        props.setProperty("explosions.block-in-claims", String.valueOf(blockClaimExplosions));
        props.setProperty("explosions.block-surface-creeper", String.valueOf(blockSurfaceCreeperExplosions));
        props.setProperty("explosions.block-surface-other", String.valueOf(blockSurfaceOtherExplosions));

        props.setProperty("fire.spreads", String.valueOf(fireSpreads));
        props.setProperty("fire.destroys", String.valueOf(fireDestroys));

        props.setProperty("entity.endermen-move-blocks", String.valueOf(endermenMoveBlocks));
        props.setProperty("entity.silverfish-break-blocks", String.valueOf(silverfishBreakBlocks));
        props.setProperty("entity.creatures-trample-crops", String.valueOf(creaturesTrampleCrops));
        props.setProperty("entity.rabbits-eat-crops", String.valueOf(rabbitsEatCrops));
        props.setProperty("entity.zombies-break-doors", String.valueOf(zombiesBreakDoors));

        props.setProperty("trees.limit-sky-trees", String.valueOf(limitSkyTrees));
        props.setProperty("trees.limit-growth", String.valueOf(limitTreeGrowth));
        props.setProperty("pistons.mode", pistonMode.name());

        props.setProperty("spam.enabled", String.valueOf(spamEnabled));
        props.setProperty("admin.smart-ban", String.valueOf(smartBan));
        props.setProperty("admin.max-players-per-ip", String.valueOf(maxPlayersPerIp));

        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Alloy GriefPrevention Configuration");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save GriefPrevention config to " + file, e);
        }
    }

    private int intProp(Properties props, String key, int def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    private double doubleProp(Properties props, String key, double def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        try { return Double.parseDouble(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    private boolean boolProp(Properties props, String key, boolean def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        return Boolean.parseBoolean(val.trim());
    }

    public enum PistonMode {
        IGNORED,
        CLAIMS_ONLY,
        EVERYWHERE_SIMPLE
    }
}
