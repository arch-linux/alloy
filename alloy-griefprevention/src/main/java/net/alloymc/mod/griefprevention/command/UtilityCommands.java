package net.alloymc.mod.griefprevention.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.entity.Player;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility commands: /trapped, /unlockdrops, /ignoreplayer, /unignoreplayer,
 * /ignoredplayerlist, /softmute, /separate, /unseparate.
 */
public final class UtilityCommands {

    private UtilityCommands() {}

    public static void registerAll(GriefPreventionMod mod) {
        mod.registerCommand(new TrappedCommand(mod));
        mod.registerCommand(new UnlockDropsCommand(mod));
        mod.registerCommand(new IgnorePlayerCommand(mod));
        mod.registerCommand(new UnignorePlayerCommand(mod));
        mod.registerCommand(new IgnoredPlayerListCommand(mod));
        mod.registerCommand(new SoftMuteCommand(mod));
        mod.registerCommand(new SeparateCommand(mod));
        mod.registerCommand(new UnseparateCommand(mod));
    }

    // ---- /trapped ----

    static class TrappedCommand extends Command {
        private final GriefPreventionMod mod;
        TrappedCommand(GriefPreventionMod mod) {
            super("trapped", "Ejects you from someone else's claim",
                    "griefprevention.trapped", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

            // Cooldown check (5 minutes)
            long cooldown = 300000;
            if (System.currentTimeMillis() - data.lastRescueTimestamp() < cooldown) {
                player.sendMessage(Messages.TRAPPED_COOLDOWN, Player.MessageType.ERROR);
                return true;
            }

            var claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null) {
                player.sendMessage("\u00a7cYou're not trapped in a claim.", Player.MessageType.ERROR);
                return true;
            }

            if (claim.isAdminClaim() && !mod.config().claimsAllowTrappedInAdminClaims) {
                player.sendMessage("\u00a7cYou can't use /trapped in admin claims.", Player.MessageType.ERROR);
                return true;
            }

            data.setPendingRescue(true);
            data.setLastRescueTimestamp(System.currentTimeMillis());
            player.sendMessage(Messages.TRAPPED_PENDING, Player.MessageType.INFO);

            // Rescue after 10 seconds if player hasn't moved
            var savedLoc = player.location();
            AlloyAPI.scheduler().runTaskLater(() -> {
                if (!player.isOnline()) return;
                if (!data.isPendingRescue()) return;

                double dist = player.location().distanceSquared(savedLoc);
                if (dist > 4.0) { // Moved more than 2 blocks
                    player.sendMessage(Messages.TRAPPED_MOVED, Player.MessageType.ERROR);
                    data.setPendingRescue(false);
                    return;
                }

                // Find a safe location outside the claim
                // Move player to just outside the nearest edge
                int px = (int) Math.floor(player.location().x());
                int pz = (int) Math.floor(player.location().z());

                int distToWest = px - claim.lesserX();
                int distToEast = claim.greaterX() - px;
                int distToNorth = pz - claim.lesserZ();
                int distToSouth = claim.greaterZ() - pz;

                int min = Math.min(Math.min(distToWest, distToEast), Math.min(distToNorth, distToSouth));

                int newX = px, newZ = pz;
                if (min == distToWest) newX = claim.lesserX() - 2;
                else if (min == distToEast) newX = claim.greaterX() + 2;
                else if (min == distToNorth) newZ = claim.lesserZ() - 2;
                else newZ = claim.greaterZ() + 2;

                var dest = new net.alloymc.api.world.Location(player.location().world(), newX + 0.5, player.location().y(), newZ + 0.5);
                player.teleport(dest);
                player.sendMessage(Messages.TRAPPED_RESCUED, Player.MessageType.SUCCESS);
                data.setPendingRescue(false);
            }, 200L); // 10 seconds = 200 ticks

            return true;
        }
    }

    // ---- /unlockdrops ----

    static class UnlockDropsCommand extends Command {
        private final GriefPreventionMod mod;
        UnlockDropsCommand(GriefPreventionMod mod) {
            super("unlockdrops", "Allows others to pick up your death drops",
                    "griefprevention.unlockdrops", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            data.setDropsUnlocked(true);
            player.sendMessage(Messages.DROPS_UNLOCKED, Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- /ignoreplayer ----

    static class IgnorePlayerCommand extends Command {
        private final GriefPreventionMod mod;
        IgnorePlayerCommand(GriefPreventionMod mod) {
            super("ignoreplayer", "Ignores another player's chat messages",
                    "griefprevention.ignore", List.of("ignore"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/ignoreplayer <player>")); return true; }
            Player player = (Player) sender;

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { player.sendMessage(Messages.PLAYER_NOT_FOUND, Player.MessageType.ERROR); return true; }
            Player target = opt.get();

            if (target.uniqueId().equals(player.uniqueId())) {
                player.sendMessage("\u00a7cYou can't ignore yourself.", Player.MessageType.ERROR);
                return true;
            }

            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            data.setIgnoring(target.uniqueId(), true);
            player.sendMessage(String.format(Messages.PLAYER_IGNORED, target.displayName()),
                    Player.MessageType.SUCCESS);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /unignoreplayer ----

    static class UnignorePlayerCommand extends Command {
        private final GriefPreventionMod mod;
        UnignorePlayerCommand(GriefPreventionMod mod) {
            super("unignoreplayer", "Unignores a player's chat messages",
                    "griefprevention.ignore", List.of("unignore"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/unignoreplayer <player>")); return true; }
            Player player = (Player) sender;

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { player.sendMessage(Messages.PLAYER_NOT_FOUND, Player.MessageType.ERROR); return true; }
            Player target = opt.get();

            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
            data.setIgnoring(target.uniqueId(), false);
            player.sendMessage(String.format(Messages.PLAYER_UNIGNORED, target.displayName()),
                    Player.MessageType.SUCCESS);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /ignoredplayerlist ----

    static class IgnoredPlayerListCommand extends Command {
        private final GriefPreventionMod mod;
        IgnoredPlayerListCommand(GriefPreventionMod mod) {
            super("ignoredplayerlist", "Lists players you're ignoring",
                    "griefprevention.ignore", List.of("ignores", "ignored", "ignorelist", "ignoredlist", "listignores"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;
            PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());

            List<String> names = new ArrayList<>();
            for (Map.Entry<UUID, Boolean> entry : data.ignoredPlayers().entrySet()) {
                if (entry.getValue()) {
                    var opt = AlloyAPI.server().player(entry.getKey());
                    names.add(opt.isPresent() ? opt.get().displayName() : entry.getKey().toString());
                }
            }

            if (names.isEmpty()) {
                player.sendMessage("\u00a77You're not ignoring anyone.", Player.MessageType.INFO);
            } else {
                player.sendMessage("\u00a7eIgnored players: " + String.join(", ", names), Player.MessageType.INFO);
            }
            return true;
        }
    }

    // ---- /softmute ----

    static class SoftMuteCommand extends Command {
        private final GriefPreventionMod mod;
        SoftMuteCommand(GriefPreventionMod mod) {
            super("softmute", "Toggles soft-mute for a player",
                    "griefprevention.softmute", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/softmute <player>")); return true; }

            var opt = AlloyAPI.server().player(args[0]);
            if (opt.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }
            Player target = opt.get();

            PlayerData data = mod.claimManager().getPlayerData(target.uniqueId());
            data.setSoftMuted(!data.isSoftMuted());
            mod.dataStore().savePlayerData(target.uniqueId(), data);

            sender.sendMessage(String.format(
                    data.isSoftMuted() ? Messages.SOFT_MUTE_ON : Messages.SOFT_MUTE_OFF,
                    target.displayName()));
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /separate ----

    static class SeparateCommand extends Command {
        private final GriefPreventionMod mod;
        SeparateCommand(GriefPreventionMod mod) {
            super("separate", "Forces two players to ignore each other",
                    "griefprevention.separate", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 2) { sender.sendMessage(String.format(Messages.USAGE, "/separate <player1> <player2>")); return true; }

            var opt1 = AlloyAPI.server().player(args[0]);
            var opt2 = AlloyAPI.server().player(args[1]);
            if (opt1.isEmpty() || opt2.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }

            PlayerData d1 = mod.claimManager().getPlayerData(opt1.get().uniqueId());
            PlayerData d2 = mod.claimManager().getPlayerData(opt2.get().uniqueId());
            d1.setIgnoring(opt2.get().uniqueId(), true);
            d2.setIgnoring(opt1.get().uniqueId(), true);

            sender.sendMessage("\u00a7a" + opt1.get().displayName() + " and " + opt2.get().displayName() + " are now separated.");
            return true;
        }
    }

    // ---- /unseparate ----

    static class UnseparateCommand extends Command {
        private final GriefPreventionMod mod;
        UnseparateCommand(GriefPreventionMod mod) {
            super("unseparate", "Reverses /separate for two players",
                    "griefprevention.separate", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length < 2) { sender.sendMessage(String.format(Messages.USAGE, "/unseparate <player1> <player2>")); return true; }

            var opt1 = AlloyAPI.server().player(args[0]);
            var opt2 = AlloyAPI.server().player(args[1]);
            if (opt1.isEmpty() || opt2.isEmpty()) { sender.sendMessage(Messages.PLAYER_NOT_FOUND); return true; }

            PlayerData d1 = mod.claimManager().getPlayerData(opt1.get().uniqueId());
            PlayerData d2 = mod.claimManager().getPlayerData(opt2.get().uniqueId());
            d1.setIgnoring(opt2.get().uniqueId(), false);
            d2.setIgnoring(opt1.get().uniqueId(), false);

            sender.sendMessage("\u00a7a" + opt1.get().displayName() + " and " + opt2.get().displayName() + " are no longer separated.");
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
