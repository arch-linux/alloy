package net.alloymc.mod.griefprevention.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.entity.Player;
import net.alloymc.mod.griefprevention.GriefPreventionMod;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.message.Messages;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commands for managing trust levels: /trust, /untrust, /containertrust,
 * /accesstrust, /permissiontrust, /trustlist, /restrictsubclaim.
 */
public final class TrustCommands {

    private TrustCommands() {}

    public static void registerAll(GriefPreventionMod mod) {
        mod.registerCommand(new TrustCommand(mod));
        mod.registerCommand(new ContainerTrustCommand(mod));
        mod.registerCommand(new AccessTrustCommand(mod));
        mod.registerCommand(new PermissionTrustCommand(mod));
        mod.registerCommand(new UntrustCommand(mod));
        mod.registerCommand(new TrustListCommand(mod));
        mod.registerCommand(new RestrictSubclaimCommand(mod));
    }

    // ---- Shared trust-granting logic ----

    private static boolean grantTrust(GriefPreventionMod mod, Player player, String targetName,
                                       ClaimPermission level, String levelLabel) {
        // Resolve target
        String targetId;
        if (targetName.equalsIgnoreCase("public") || targetName.equalsIgnoreCase("all")) {
            targetId = "public";
            targetName = "public";
        } else {
            var opt = AlloyAPI.server().player(targetName);
            if (opt.isEmpty()) {
                player.sendMessage(Messages.PLAYER_NOT_FOUND, Player.MessageType.ERROR);
                return true;
            }
            targetId = opt.get().uniqueId().toString();
            targetName = opt.get().displayName();
        }

        PlayerData data = mod.claimManager().getPlayerData(player.uniqueId());
        Claim claim = mod.claimManager().getClaimAt(player.location());

        if (claim == null) {
            // Apply to ALL claims
            Collection<Claim> claims = mod.claimManager().getClaimsForPlayer(player.uniqueId());
            if (claims.isEmpty()) {
                player.sendMessage(Messages.NO_CLAIM_HERE, Player.MessageType.ERROR);
                return true;
            }
            for (Claim c : claims) {
                c.setPermission(targetId, level);
                mod.dataStore().saveClaim(c);
            }
            player.sendMessage(String.format(Messages.TRUST_GRANTED, targetName, levelLabel, "all your claims"),
                    Player.MessageType.SUCCESS);
        } else {
            // Check permission
            ClaimPermission required = (level == ClaimPermission.MANAGE) ? ClaimPermission.EDIT : ClaimPermission.MANAGE;
            String denial = claim.checkPermission(player.uniqueId(), required);
            if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
                player.sendMessage(denial, Player.MessageType.ERROR);
                return true;
            }

            claim.setPermission(targetId, level);
            mod.dataStore().saveClaim(claim);
            player.sendMessage(String.format(Messages.TRUST_GRANTED, targetName, levelLabel, "this claim"),
                    Player.MessageType.SUCCESS);
        }
        return true;
    }

    // ---- /trust (BUILD) ----

    static class TrustCommand extends Command {
        private final GriefPreventionMod mod;
        TrustCommand(GriefPreventionMod mod) {
            super("trust", "Grants a player build permission in your claim",
                    "griefprevention.claims", List.of("tr"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/trust <player>")); return true; }
            return grantTrust(mod, (Player) sender, args[0], ClaimPermission.BUILD, "build");
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /containertrust ----

    static class ContainerTrustCommand extends Command {
        private final GriefPreventionMod mod;
        ContainerTrustCommand(GriefPreventionMod mod) {
            super("containertrust", "Grants container access in your claim",
                    "griefprevention.claims", List.of("ct"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/containertrust <player>")); return true; }
            return grantTrust(mod, (Player) sender, args[0], ClaimPermission.CONTAINER, "container");
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /accesstrust ----

    static class AccessTrustCommand extends Command {
        private final GriefPreventionMod mod;
        AccessTrustCommand(GriefPreventionMod mod) {
            super("accesstrust", "Grants basic access in your claim",
                    "griefprevention.claims", List.of("at"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/accesstrust <player>")); return true; }
            return grantTrust(mod, (Player) sender, args[0], ClaimPermission.ACCESS, "access");
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /permissiontrust ----

    static class PermissionTrustCommand extends Command {
        private final GriefPreventionMod mod;
        PermissionTrustCommand(GriefPreventionMod mod) {
            super("permissiontrust", "Grants management permission in your claim",
                    "griefprevention.claims", List.of("pt", "managetrust"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/permissiontrust <player>")); return true; }
            return grantTrust(mod, (Player) sender, args[0], ClaimPermission.MANAGE, "management");
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) return onlinePlayerNames(args[0]);
            return List.of();
        }
    }

    // ---- /untrust ----

    static class UntrustCommand extends Command {
        private final GriefPreventionMod mod;
        UntrustCommand(GriefPreventionMod mod) {
            super("untrust", "Revokes a player's access to your claim",
                    "griefprevention.claims", List.of());
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            if (args.length < 1) { sender.sendMessage(String.format(Messages.USAGE, "/untrust <player|all>")); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());

            if (args[0].equalsIgnoreCase("all")) {
                // Clear all permissions
                if (claim != null) {
                    String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.MANAGE);
                    if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
                        player.sendMessage(denial, Player.MessageType.ERROR);
                        return true;
                    }
                    claim.clearPermissions();
                    mod.dataStore().saveClaim(claim);
                    player.sendMessage(String.format(Messages.TRUST_ALL_REVOKED, "this claim"),
                            Player.MessageType.SUCCESS);
                } else {
                    for (Claim c : mod.claimManager().getClaimsForPlayer(player.uniqueId())) {
                        c.clearPermissions();
                        mod.dataStore().saveClaim(c);
                    }
                    player.sendMessage(String.format(Messages.TRUST_ALL_REVOKED, "all your claims"),
                            Player.MessageType.SUCCESS);
                }
                return true;
            }

            // Resolve target
            String targetId;
            String targetName = args[0];
            if (targetName.equalsIgnoreCase("public")) {
                targetId = "public";
            } else {
                var opt = AlloyAPI.server().player(targetName);
                if (opt.isEmpty()) {
                    player.sendMessage(Messages.PLAYER_NOT_FOUND, Player.MessageType.ERROR);
                    return true;
                }
                targetId = opt.get().uniqueId().toString();
                targetName = opt.get().displayName();
            }

            if (claim != null) {
                String denial = claim.checkPermission(player.uniqueId(), ClaimPermission.MANAGE);
                if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
                    player.sendMessage(denial, Player.MessageType.ERROR);
                    return true;
                }
                claim.removePermission(targetId);
                mod.dataStore().saveClaim(claim);
                player.sendMessage(String.format(Messages.TRUST_REVOKED, targetName, "this claim"),
                        Player.MessageType.SUCCESS);
            } else {
                for (Claim c : mod.claimManager().getClaimsForPlayer(player.uniqueId())) {
                    c.removePermission(targetId);
                    mod.dataStore().saveClaim(c);
                }
                player.sendMessage(String.format(Messages.TRUST_REVOKED, targetName, "all your claims"),
                        Player.MessageType.SUCCESS);
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>(onlinePlayerNames(args[0]));
                if ("all".startsWith(args[0].toLowerCase())) completions.add("all");
                return completions;
            }
            return List.of();
        }
    }

    // ---- /trustlist ----

    static class TrustListCommand extends Command {
        private final GriefPreventionMod mod;
        TrustListCommand(GriefPreventionMod mod) {
            super("trustlist", "Lists permissions for the claim you're in",
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

            player.sendMessage(Messages.TRUST_LIST_HEADER, Player.MessageType.INFO);

            List<String> managers = new ArrayList<>();
            List<String> builders = new ArrayList<>();
            List<String> containers = new ArrayList<>();
            List<String> accessors = new ArrayList<>();

            for (Map.Entry<String, ClaimPermission> entry : claim.permissions().entrySet()) {
                String name = resolveDisplayName(entry.getKey());
                switch (entry.getValue()) {
                    case MANAGE -> managers.add(name);
                    case BUILD -> builders.add(name);
                    case CONTAINER -> containers.add(name);
                    case ACCESS -> accessors.add(name);
                    default -> {} // EDIT is owner-only, not shown
                }
            }
            for (String m : claim.managers()) {
                managers.add(resolveDisplayName(m));
            }

            if (!managers.isEmpty()) player.sendMessage(String.format(Messages.TRUST_LIST_MANAGERS, String.join(", ", managers)));
            if (!builders.isEmpty()) player.sendMessage(String.format(Messages.TRUST_LIST_BUILDERS, String.join(", ", builders)));
            if (!containers.isEmpty()) player.sendMessage(String.format(Messages.TRUST_LIST_CONTAINERS, String.join(", ", containers)));
            if (!accessors.isEmpty()) player.sendMessage(String.format(Messages.TRUST_LIST_ACCESSORS, String.join(", ", accessors)));

            if (managers.isEmpty() && builders.isEmpty() && containers.isEmpty() && accessors.isEmpty()) {
                player.sendMessage("\u00a77  No one is trusted in this claim.");
            }
            return true;
        }

        private String resolveDisplayName(String id) {
            if (id.equalsIgnoreCase("public")) return "public";
            try {
                UUID uuid = UUID.fromString(id);
                var opt = AlloyAPI.server().player(uuid);
                if (opt.isPresent()) return opt.get().displayName();
            } catch (IllegalArgumentException ignored) {}
            return id;
        }
    }

    // ---- /restrictsubclaim ----

    static class RestrictSubclaimCommand extends Command {
        private final GriefPreventionMod mod;
        RestrictSubclaimCommand(GriefPreventionMod mod) {
            super("restrictsubclaim", "Toggles subclaim permission inheritance",
                    "griefprevention.claims", List.of("rsc"));
            this.mod = mod;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!sender.isPlayer()) { sender.sendMessage(Messages.PLAYER_ONLY); return true; }
            Player player = (Player) sender;

            Claim claim = mod.claimManager().getClaimAt(player.location());
            if (claim == null || !claim.isSubclaim()) {
                player.sendMessage("\u00a7cYou must be standing in a subclaim.", Player.MessageType.ERROR);
                return true;
            }

            Claim parent = claim.parent();
            String denial = parent.checkPermission(player.uniqueId(), ClaimPermission.EDIT);
            if (denial != null && !player.hasPermission("griefprevention.adminclaims")) {
                player.sendMessage(Messages.NOT_YOUR_CLAIM, Player.MessageType.ERROR);
                return true;
            }

            claim.setInheritNothing(!claim.inheritNothing());
            mod.dataStore().saveClaim(parent);

            player.sendMessage(claim.inheritNothing() ? Messages.SUBCLAIM_RESTRICTED : Messages.SUBCLAIM_UNRESTRICTED,
                    Player.MessageType.SUCCESS);
            return true;
        }
    }

    // ---- Helpers ----

    private static List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase();
        List<String> names = new ArrayList<>();
        for (var p : AlloyAPI.server().onlinePlayers()) {
            if (p.name().toLowerCase().startsWith(lower)) {
                names.add(p.name());
            }
        }
        return names;
    }
}
