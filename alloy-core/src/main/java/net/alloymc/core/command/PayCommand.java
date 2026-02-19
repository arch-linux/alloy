package net.alloymc.core.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.economy.EconomyProvider;
import net.alloymc.api.entity.Player;

import java.util.List;

/**
 * Transfers money from the sender to another player.
 *
 * <p>Usage: /pay <player> <amount>
 */
public class PayCommand extends Command {

    public PayCommand() {
        super("pay", "Pay another player", "alloy.command.pay");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.isPlayer()) {
            sender.sendMessage("\u00a7cOnly players can use /pay. Use /setmoney instead.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("\u00a7cUsage: /pay <player> <amount>");
            return true;
        }

        EconomyProvider economy = AlloyAPI.economy().provider();
        if (economy == null) {
            sender.sendMessage("\u00a7cNo economy provider is active.");
            return true;
        }

        Player player = (Player) sender;
        String targetName = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00a7cInvalid amount: " + args[1]);
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("\u00a7cAmount must be positive.");
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
        if (targetPlayer.uniqueId().equals(player.uniqueId())) {
            sender.sendMessage("\u00a7cYou can't pay yourself.");
            return true;
        }

        String fmt = AlloyAPI.economy().formatAmount(amount);
        boolean success = economy.transfer(player.uniqueId(), targetPlayer.uniqueId(), amount);
        if (!success) {
            sender.sendMessage("\u00a7cInsufficient funds. Your balance: "
                    + AlloyAPI.economy().formatAmount(economy.getBalance(player.uniqueId())));
            return true;
        }

        sender.sendMessage("\u00a7aPaid \u00a7f" + fmt + "\u00a7a to \u00a7f" + targetPlayer.name()
                + "\u00a7a. New balance: \u00a7f"
                + AlloyAPI.economy().formatAmount(economy.getBalance(player.uniqueId())));
        targetPlayer.sendMessage("\u00a7aReceived \u00a7f" + fmt + "\u00a7a from \u00a7f" + player.name()
                + "\u00a7a. New balance: \u00a7f"
                + AlloyAPI.economy().formatAmount(economy.getBalance(targetPlayer.uniqueId())));

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
