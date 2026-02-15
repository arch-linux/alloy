package net.alloymc.mod.griefprevention.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.entity.Player;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ShovelMode;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Admin commands: mode switching, claim deletion, claim block management, transfers, etc.
 */
public final class AdminCommands {

    private AdminCommands() {}

    public static void registerAll(GriefPreventionMod mod) {
        mod.registerCommand(new AdminClaimsCommand(mod));
        mod.registerCommand(new BasicClaimsCommand(mod));
        mod.registerCommand(new SubdivideClaimsCommand(mod));
        mod.registerCommand(new IgnoreClaimsCommand(mod));
        mod.registerCommand(new DeleteClaimCommand(mod));
        mod.registerCommand(new DeleteAllClaimsCommand(mod));
        mod.registerCommand(new TransferClaimCommand(mod));
        mod.registerCommand(new AdjustBonusCommand(mod));
        mod.registerCommand(new AdjustBonusAllCommand(mod));
        mod.registerCommand(new SetAccruedCommand(mod));
        mod.registerCommand(new ReloadCommand(mod));
    }

    // ---- /adminclaims ----

    static class AdminClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        AdminClaimsCommand(GriefPreventionMod mod) {
            super("adminclaims", "Switches shovel to admin claim mode",
                    "griefprevention.adminclaims", List.of("ac"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            mod.claimManager().getPlayerData(player.uniqueId()).setShovelMode(ShovelMode.ADMIN);
            player.sendMessage(Messages.SHOVEL_ADMIN, Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /basicclaims ----

    static class BasicClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        BasicClaimsCommand(GriefPreventionMod mod) {
            super("basicclaims", "Switches shovel to basic claim mode",
                    "griefprevention.claims", List.of("bc"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            mod.claimManager().getPlayerData(player.uniqueId()).setShovelMode(ShovelMode.BASIC);
            player.sendMessage(Messages.SHOVEL_BASIC, Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /subdivideclaims ----

    static class SubdivideClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        SubdivideClaimsCommand(GriefPreventionMod mod) {
            super("subdivideclaims", "Switches shovel to subdivision mode",
                    "griefprevention.claims", List.of("sc", "subdivideclaim"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            mod.claimManager().getPlayerData(player.uniqueId()).setShovelMode(ShovelMode.SUBDIVIDE);
            player.sendMessage(Messages.SHOVEL_SUBDIVIDE, Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /ignoreclaims ----

    static class IgnoreClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        IgnoreClaimsCommand(GriefPreventionMod mod) {
            super("ignoreclaims", "Toggles ignore claims mode",
                    "griefprevention.ignoreclaims", List.of("ic"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            data.setIgnoringClaims(!data.isIgnoringClaims());
            player.sendMessage(data.isIgnoringClaims() ? Messages.IGNORE_CLAIMS_ON : Messages.IGNORE_CLAIMS_OFF,
                    Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /deleteclaim ----

    static class DeleteClaimCommand extends Command {
        private final GriefPreventionMod mod;
        DeleteClaimCommand(GriefPreventionMod mod) {
            super("deleteclaim", "Deletes the claim you're standing in",
                    "griefprevention.deleteclaims", List.of());
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

            mod.dataStore().deleteClaim(claim);
            mod.claimManager().deleteClaim(claim);
            player.sendMessage(Messages.CLAIM_DELETED, Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /deleteallclaims ----

    static class DeleteAllClaimsCommand extends Command {
        private final GriefPreventionMod mod;
        DeleteAllClaimsCommand(GriefPreventionMod mod) {
            super("deleteallclaims", "Deletes all claims belonging to a player",
                    "griefprevention.deleteclaims", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(String.format(Messages.USAGE, "/deleteallclaims <player>")); return true;
            }

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }
            Player target = opt.get();

            Collection<Claim> claims = List.copyOf(mod.claimManager().getClaimsForPlayer(target.uniqueId()));
            for (Claim claim : claims) {
                mod.dataStore().deleteClaim(claim);
                mod.claimManager().deleteClaim(claim);
            }

            sender.sendMessage(String.format(Messages.ALL_CLAIMS_DELETED, target.displayName()));
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /transferclaim ----

    static class TransferClaimCommand extends Command {
        private final GriefPreventionMod mod;
        TransferClaimCommand(GriefPreventionMod mod) {
            super("transferclaim", "Transfers an admin claim to a player",
                    "griefprevention.transferclaim", List.of("giveclaim"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/transferclaim <player>")); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { player.sendMessage(Messages.PLAYER_NOT_FOUND, Player.MessageType.ERROR); return true; }
            Player target = opt.get();

            claim.setOwnerID(target.uniqueId());
            mod.claimManager().getPlayerData(target.uniqueId()).claims().add(claim);
            mod.dataStore().saveClaim(claim);

            player.sendMessage(String.format(Messages.CLAIM_TRANSFERRED, target.displayName()),
                    Player.MessageType.SUCCESS);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /adjustbonusclaimblocks ----

    static class AdjustBonusCommand extends Command {
        private final GriefPreventionMod mod;
        AdjustBonusCommand(GriefPreventionMod mod) {
            super("adjustbonusclaimblocks", "Adds/subtracts bonus claim blocks",
                    "griefprevention.adjustclaimblocks", List.of("acb"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(String.format(Messages.USAGE, "/adjustbonusclaimblocks <player> <amount>")); return true;
            }

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }
            Player target = opt.get();

            int amount;
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                sender.sendMessage(String.format(Messages.USAGE, "/adjustbonusclaimblocks <player> <amount>")); return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(target.uniqueId());
            data.addBonusClaimBlocks(amount);
            mod.dataStore().savePlayerData(target.uniqueId(), data);

            sender.sendMessage(String.format(Messages.BONUS_BLOCKS_ADJUSTED,
                    target.displayName(), amount, data.bonusClaimBlocks()));
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /adjustbonusclaimblocksall ----

    static class AdjustBonusAllCommand extends Command {
        private final GriefPreventionMod mod;
        AdjustBonusAllCommand(GriefPreventionMod mod) {
            super("adjustbonusclaimblocksall", "Adds/subtracts bonus blocks for all online players",
                    "griefprevention.adjustclaimblocks", List.of("acball"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(String.format(Messages.USAGE, "/adjustbonusclaimblocksall <amount>")); return true;
            }

            int amount;
            try { amount = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                sender.sendMessage(String.format(Messages.USAGE, "/adjustbonusclaimblocksall <amount>")); return true;
            }

            int count = 0;
            for (var p : AlloyAPI.server().onlinePlayers()) {
                if (p instanceof Player player) {
                    PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
                    data.addBonusClaimBlocks(amount);
                    mod.dataStore().savePlayerData(player.uniqueId(), data);
                    count++;
                }
            }

            sender.sendMessage("\u00a7aAdjusted bonus claim blocks by " + amount + " for " + count + " players.");
            return true;
        }
    }

    // ---- /setaccruedclaimblocks ----

    static class SetAccruedCommand extends Command {
        private final GriefPreventionMod mod;
        SetAccruedCommand(GriefPreventionMod mod) {
            super("setaccruedclaimblocks", "Sets a player's accrued claim blocks",
                    "griefprevention.adjustclaimblocks", List.of("scb"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(String.format(Messages.USAGE, "/setaccruedclaimblocks <player> <amount>")); return true;
            }

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }
            Player target = opt.get();

            int amount;
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                sender.sendMessage(String.format(Messages.USAGE, "/setaccruedclaimblocks <player> <amount>")); return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(target.uniqueId());
            data.setAccruedClaimBlocks(amount);
            mod.dataStore().savePlayerData(target.uniqueId(), data);

            sender.sendMessage(String.format(Messages.ACCRUED_BLOCKS_SET, target.displayName(), amount));
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /gpreload ----

    static class ReloadCommand extends Command {
        private final GriefPreventionMod mod;
        ReloadCommand(GriefPreventionMod mod) {
            super("gpreload", "Reloads GriefPrevention configuration",
                    "griefprevention.reload", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            mod.reloadConfig();
            sender.sendMessage(Messages.CONFIG_RELOADED);
            return true;
        }
    }

    // ---- Helpers ----

    private static List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        List<String> names = new ArrayList<>();
        for (var p : AlloyAPI.server().onlinePlayers()) {
            if (p.name().toLowerCase().startsWith(lower)) names.add(p.name());
        }
        return names;
    }
}
