package net.alloymc.core.command;

import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.core.monitor.TpsMonitor;

/**
 * Shows current TPS (ticks per second) and MSPT (milliseconds per tick).
 */
public class TpsCommand extends Command {

    private final TpsMonitor monitor;

    public TpsCommand(TpsMonitor monitor) {
        super("tps", "Show server TPS and MSPT", "alloy.command.tps");
        this.monitor = monitor;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        double tps = monitor.tps();
        double mspt = monitor.mspt();

        String tpsColor = tps >= 19.0 ? "good" : tps >= 15.0 ? "moderate" : "poor";
        sender.sendMessage(String.format("[Alloy] TPS: %.1f (%s) | MSPT: %.2fms",
                tps, tpsColor, mspt));

        return true;
    }
}
