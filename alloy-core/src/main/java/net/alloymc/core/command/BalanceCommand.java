package net.alloymc.core.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.economy.EconomyProvider;
import net.alloymc.api.entity.Player;

import java.util.List;

/**
 * Shows a player's balance. Players can check their own balance, ops can check others.
 *
 * <p>Usage: /balance [player]
 */
public class BalanceCommand extends Command {

    public BalanceCommand() {
        super("balance", "Check your balance", "alloy.command.balance", List.of("bal", "money"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        EconomyProvider economy = AlloyAPI.economy().provider();
        if (economy == null) {
            sender.sendMessage("\u00a7cNo economy provider is active.");
            return true;
        }

        if (args.length == 0) {
            // Check own balance
            if (!sender.isPlayer()) {
                sender.sendMessage("\u00a7cConsole must specify a player: /balance <player>");
                return true;
            }
            Player player = (Player) sender;
            double balance = economy.getBalance(player.uniqueId());
            sender.sendMessage(String.format("\u00a7aBalance: \u00a7f$%.2f", balance));
        } else {
            // Check another player's balance (op only from players, always allowed from console)
            if (sender.isPlayer() && !sender.hasPermission("alloy.command.setmoney")) {
                sender.sendMessage("\u00a7cYou can only check your own balance.");
                return true;
            }
            String targetName = args[0];
            var target = AlloyAPI.server().player(targetName);
            if (target.isEmpty()) {
                sender.sendMessage("\u00a7cPlayer not found: " + targetName);
                return true;
            }
            Player targetPlayer = target.get();
            double balance = economy.getBalance(targetPlayer.uniqueId());
            sender.sendMessage(String.format("\u00a7a%s's balance: \u00a7f$%.2f",
                    targetPlayer.name(), balance));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("alloy.command.setmoney")) {
            String prefix = args[0].toLowerCase();
            return AlloyAPI.server().onlinePlayers().stream()
                    .map(Player::name)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
