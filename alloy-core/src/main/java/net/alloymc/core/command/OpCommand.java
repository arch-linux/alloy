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
 * Grants operator status to a player.
 * Updates both Alloy's permissions.json and MC's ops.json.
 */
public class OpCommand extends Command {

    private final FilePermissionProvider provider;

    public OpCommand(FilePermissionProvider provider) {
        super("op", "Grant operator status to a player", "alloy.command.op");
        this.provider = provider;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("[Alloy] Usage: /op <player>");
            return true;
        }

        String targetName = args[0];
        Optional<? extends Player> targetOpt = AlloyAPI.server().player(targetName);

        if (targetOpt.isEmpty()) {
            sender.sendMessage("[Alloy] Player '" + targetName + "' is not online.");
            return true;
        }

        Player target = targetOpt.get();

        if (target.isOp()) {
            sender.sendMessage("[Alloy] " + target.name() + " is already an operator.");
            return true;
        }

        // Update both permissions.json and ops.json
        provider.setOp(target.uniqueId(), target.name(), true);

        sender.sendMessage("[Alloy] " + target.name() + " is now an operator.");
        if (sender.isPlayer() && !sender.name().equals(target.name())) {
            target.sendMessage("[Alloy] You are now an operator.");
        }

        System.out.println("[AlloyCore] " + sender.name() + " opped " + target.name());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : AlloyAPI.server().onlinePlayers()) {
                if (!p.isOp() && p.name().toLowerCase().startsWith(prefix)) {
                    completions.add(p.name());
                }
            }
            return completions;
        }
        return List.of();
    }
}
