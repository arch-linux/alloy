package net.alloymc.core.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.entity.Player;
import net.alloymc.core.permission.FilePermissionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Revokes operator status from a player.
 * Updates both Alloy's permissions.json and MC's ops.json.
 */
public class DeopCommand extends Command {

    private final FilePermissionProvider provider;

    public DeopCommand(FilePermissionProvider provider) {
        super("deop", "Revoke operator status from a player", "alloy.command.deop");
        this.provider = provider;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("[Alloy] Usage: /deop <player>");
            return true;
        }

        String targetName = args[0];
        Optional<? extends Player> targetOpt = AlloyAPI.server().player(targetName);

        if (targetOpt.isEmpty()) {
            sender.sendMessage("[Alloy] Player '" + targetName + "' is not online.");
            return true;
        }

        Player target = targetOpt.get();

        if (!target.isOp()) {
            sender.sendMessage("[Alloy] " + target.name() + " is not an operator.");
            return true;
        }

        // Update both permissions.json and ops.json
        provider.setOp(target.uniqueId(), target.name(), false);

        sender.sendMessage("[Alloy] " + target.name() + " is no longer an operator.");
        if (sender.isPlayer() && !sender.name().equals(target.name())) {
            target.sendMessage("[Alloy] You are no longer an operator.");
        }

        System.out.println("[AlloyCore] " + sender.name() + " deopped " + target.name());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : AlloyAPI.server().onlinePlayers()) {
                if (p.isOp() && p.name().toLowerCase().startsWith(prefix)) {
                    completions.add(p.name());
                }
            }
            return completions;
        }
        return List.of();
    }
}
