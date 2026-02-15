package net.alloymc.mod.griefprevention.command;

import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.entity.Player;
import net.alloymc.api.world.Location;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.claim.CreateClaimResult;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;
import net.alloymc.mod.griefprevention.visualization.ClaimVisualization;
import net.alloymc.mod.griefprevention.visualization.VisualizationType;

import java.util.Collection;
import java.util.List;

/**
 * Commands for creating, abandoning, extending, and listing claims.
 */
public final class ClaimCommands {

    private ClaimCommands() {}

    public static void registerAll(GriefPreventionMod mod) {
        mod.registerCommand(new ClaimCommand(mod));
        mod.registerCommand(new AbandonClaimCommand(mod));
        mod.registerCommand(new AbandonTopLevelClaimCommand(mod));
        mod.registerCommand(new AbandonAllClaimsCommand(mod));
        mod.registerCommand(new ExtendClaimCommand(mod));
        mod.registerCommand(new ClaimsListCommand(mod));
        mod.registerCommand(new ClaimExplosionsCommand(mod));
    }

    // ---- /claim ----

    static class ClaimCommand extends Command {
        private final GriefPreventionMod mod;
        ClaimCommand(GriefPreventionMod mod) {
            super("claim", "Creates a land claim centered at your location",
                    "griefprevention.claims", List.of("createclaim", "makeclaim", "newclaim"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

            int radius = mod.config().claimsAutoClaimRadius;
            if (args.length > 0) {
                try { radius = Integer.parseInt(args[0]); }
                catch (NumberFormatException e) {
                    sender.sendMessage(String.format(Messages.USAGE, "/claim [radius]")); return true;
                }
            }
            if (radius < 1) radius = 1;

            Location loc = player.location();
            int x = loc.toBlockPosition().x();
            int z = loc.toBlockPosition().z();
            int y = loc.toBlockPosition().y() - mod.config().claimsExtendIntoGround;

            CreateClaimResult result = mod.claimManager().createClaim(
                    player.uniqueId(), loc.world().name(),
                    x - radius, y, z - radius,
                    x + radius, loc.world().maxHeight(), z + radius,
                    null
            );

            if (result.succeeded()) {
                mod.dataStore().saveClaim(result.claim());
                player.sendMessage(Messages.CLAIM_CREATED, Player.MessageType.SUCCESS);
                ClaimVisualization.show(player, result.claim(), VisualizationType.CLAIM);
            } else {
                player.sendMessage(result.failureReason(), Player.MessageType.ERROR);
            }
            return true;
        }
    }

    // ---- /abandonclaim ----

    static class AbandonClaimCommand extends Command {
        private final GriefPreventionMod mod;
        AbandonClaimCommand(GriefPreventionMod mod) {
            super("abandonclaim", "Deletes the claim you're standing in",
                    "griefprevention.claims", List.of("unclaim", "declaim", "removeclaim", "disclaim"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }
            if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims")) {
                player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
                return true;
            }
            if (claim.ownerID() != null && !claim.ownerID().equals(player.uniqueId())
                    && !player.hasPermission("griefprevention.deleteclaims")) {
                player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
                return true;
            }

            // Return claim blocks
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            int returnedBlocks = (int) (claim.area() * mod.config().claimsAbandonReturnRatio);

            mod.dataStore().deleteClaim(claim);
            mod.claimManager().deleteClaim(claim);

            if (claim.ownerID() != null && claim.ownerID().equals(player.uniqueId())) {
                data.addBonusClaimBlocks(returnedBlocks);
                mod.dataStore().savePlayerData(player.uniqueId(), data);
            }

            player.sendMessage(String.format(Messages.ABANDON_SUCCESS, data.remainingClaimBlocks()),
                    Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /abandontoplevelclaim ----

    static class AbandonTopLevelClaimCommand extends Command {
        private final GriefPreventionMod mod;
        AbandonTopLevelClaimCommand(GriefPreventionMod mod) {
            super("abandontoplevelclaim", "Deletes a claim and all its subdivisions",
                    "griefprevention.claims", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }
            // Navigate to top-level
            while (claim.parent() != null) claim = claim.parent();

            if (claim.ownerID() != null && !claim.ownerID().equals(player.uniqueId())
                    && !player.hasPermission("griefprevention.deleteclaims")) {
                player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
                return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            int returnedBlocks = (int) (claim.area() * mod.config().claimsAbandonReturnRatio);

            mod.dataStore().deleteClaim(claim);
            mod.claimManager().deleteClaim(claim);

            if (claim.ownerID() != null && claim.ownerID().equals(player.uniqueId())) {
                data.addBonusClaimBlocks(returnedBlocks);
                mod.dataStore().savePlayerData(player.uniqueId(), data);
            }

            player.sendMessage(String.format(Messages.ABANDON_SUCCESS, data.remainingClaimBlocks()),
                    Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /abandonallclaims ----

    static class AbandonAllClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        AbandonAllClaimsCommand(GriefPreventionMod mod) {
            super("abandonallclaims", "Deletes ALL your claims",
                    "griefprevention.abandonallclaims", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
                player.sendMessage(Messages.ABANDON_ALL_CONFIRM, Player.MessageType.WARNING);
                return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            Collection<Claim> playerClaims = mod.claimManager().getClaimsForPlayer(player.uniqueId());

            int totalReturned = 0;
            for (Claim claim : List.copyOf(playerClaims)) {
                if (!claim.isSubclaim()) {
                    totalReturned += (int) (claim.area() * mod.config().claimsAbandonReturnRatio);
                    mod.dataStore().deleteClaim(claim);
                    mod.claimManager().deleteClaim(claim);
                }
            }

            data.addBonusClaimBlocks(totalReturned);
            mod.dataStore().savePlayerData(player.uniqueId(), data);

            player.sendMessage(String.format(Messages.ABANDON_ALL_SUCCESS, data.remainingClaimBlocks()),
                    Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /extendclaim ----

    static class ExtendClaimCommand extends Command {
        private final GriefPreventionMod mod;
        ExtendClaimCommand(GriefPreventionMod mod) {
            super("extendclaim", "Resizes claim in the direction you're facing",
                    "griefprevention.claims", List.of("expandclaim", "resizeclaim"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            if (args.length < 1) {
                sender.sendMessage(String.format(Messages.USAGE, "/extendclaim <blocks>")); return true;
            }

            int amount;
            try { amount = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                sender.sendMessage(String.format(Messages.USAGE, "/extendclaim <blocks>")); return true;
            }

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }
            if (claim.isSubclaim()) claim = claim.parent();

            if (claim.ownerID() != null && !claim.ownerID().equals(player.uniqueId())
                    && !player.hasPermission("griefprevention.deleteclaims")) {
                player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
                return true;
            }

            var face = player.facing();
            int newLX = claim.lesserX(), newLZ = claim.lesserZ();
            int newGX = claim.greaterX(), newGZ = claim.greaterZ();

            switch (face) {
                case NORTH -> newLZ -= amount;
                case SOUTH -> newGZ += amount;
                case EAST -> newGX += amount;
                case WEST -> newLX -= amount;
                default -> { /* UP/DOWN/SELF: do nothing */ }
            }

            CreateClaimResult result = mod.claimManager().resizeClaim(claim,
                    newLX, claim.lesserY(), newLZ,
                    newGX, claim.greaterY(), newGZ);

            if (result.succeeded()) {
                mod.dataStore().saveClaim(result.claim());
                PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
                player.sendMessage(String.format(Messages.CLAIM_RESIZED, data.remainingClaimBlocks()),
                        Player.MessageType.SUCCESS);
                ClaimVisualization.show(player, result.claim(),
                        claim.isAdminClaim() ? VisualizationType.ADMIN_CLAIM : VisualizationType.CLAIM);
            } else {
                player.sendMessage(result.failureReason(), Player.MessageType.ERROR);
            }
            return true;
        }
    }

    // ---- /claimslist ----

    static class ClaimsListCommand extends Command {
        private final GriefPreventionMod mod;
        ClaimsListCommand(GriefPreventionMod mod) {
            super("claimslist", "Lists your claims and claim blocks",
                    "griefprevention.claims", List.of("claimlist", "listclaims"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer() && args.length == 0) {
                sender.sendMessage(String.format(Messages.USAGE, "/claimslist [player]")); return true;
            }

            Player target;
            if (args.length > 0 && sender.hasPermission("griefprevention.claimslistother")) {
                var opt = net.alloymc.api.AlloyAPI.server().player(args[0]);
                if (opt.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }
                target = opt.get();
            } else if (sender.isPlayer()) {
                target = (Player) sender;
            } else {
                sender.sendMessage(String.format(Messages.USAGE, "/claimslist <player>")); return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(target.uniqueId());
            Collection<Claim> claims = mod.claimManager().getClaimsForPlayer(target.uniqueId());

            sender.sendMessage(String.format(Messages.CLAIMS_LIST_HEADER,
                    target.displayName(), data.claimBlocksInUse(), data.totalClaimBlocks()));

            int index = 1;
            for (Claim claim : claims) {
                if (claim.isSubclaim()) continue;
                sender.sendMessage(String.format(Messages.CLAIMS_LIST_ENTRY,
                        index++, claim.worldName(),
                        claim.lesserX(), claim.lesserZ(),
                        claim.greaterX(), claim.greaterZ(),
                        claim.area()));
            }
            if (index == 1) sender.sendMessage(Messages.CLAIMS_LIST_EMPTY);
            return true;
        }
    }

    // ---- /claimexplosions ----

    static class ClaimExplosionsCommand extends Command {
        private final GriefPreventionMod mod;
        ClaimExplosionsCommand(GriefPreventionMod mod) {
            super("claimexplosions", "Toggles whether explosives work in your claim",
                    "griefprevention.claims", List.of("claimexplosion"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }

            String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.MANAGE);
            if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
                player.sendMessage(denial, Player.MessageType.ERROR);
                return true;
            }

            claim.setExplosivesAllowed(!claim.areExplosivesAllowed());
            mod.dataStore().saveClaim(claim);

            player.sendMessage(claim.areExplosivesAllowed() ? Messages.EXPLOSIVES_ENABLED : Messages.EXPLOSIVES_DISABLED,
                    Player.MessageType.SUCCESS);
            return true;
        }
    }
}
