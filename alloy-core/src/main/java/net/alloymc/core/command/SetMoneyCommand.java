package net.alloymc.core.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.economy.EconomyProvider;
import net.alloymc.api.entity.Player;

import java.util.List;

/**
 * OP-only command to set a player's balance directly.
 *
 * <p>Usage: /setmoney <player> <amount>
 */
public class SetMoneyCommand extends Command {

    public SetMoneyCommand() {
        super("setmoney", "Set a player's balance", "alloy.command.setmoney",
                List.of("setbalance", "setbal"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("\u00a7cUsage: /setmoney <player> <amount>");
            return true;
        }

        EconomyProvider economy = AlloyAPI.economy().provider();
        if (economy == null) {
            sender.sendMessage("\u00a7cNo economy provider is active.");
            return true;
        }

        String targetName = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00a7cInvalid amount: " + args[1]);
            return true;
        }

        if (amount < 0) {
            sender.sendMessage("\u00a7cAmount cannot be negative.");
            return true;
        }

        // Round to 2 decimal places
        amount = Math.round(amount * 100.0) / 100.0;

        var target = AlloyAPI.server().player(targetName);
        if (target.isEmpty()) {
            sender.sendMessage("\u00a7cPlayer not found: " + targetName);
            return true;
        }

        Player targetPlayer = target.get();
        economy.setBalance(targetPlayer.uniqueId(), amount);
        sender.sendMessage("\u00a7aSet \u00a7f" + targetPlayer.name() + "\u00a7a's balance to \u00a7f"
                + AlloyAPI.economy().formatAmount(amount));

        // Notify the target if they're different from the sender
        if (sender.isPlayer() && !((Player) sender).uniqueId().equals(targetPlayer.uniqueId())) {
            targetPlayer.sendMessage("\u00a7aYour balance has been set to \u00a7f"
                    + AlloyAPI.economy().formatAmount(amount) + "\u00a7a by an admin.");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return AlloyAPI.server().onlinePlayers().stream()
                    .map(Player::name)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
