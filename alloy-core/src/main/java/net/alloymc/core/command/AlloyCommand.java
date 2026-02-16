package net.alloymc.core.command;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.loader.AlloyLoader;

/**
 * Shows Alloy version info, Minecraft version, and mod count.
 */
public class AlloyCommand extends Command {

    public AlloyCommand() {
        super("alloy", "Show Alloy version and info", "alloy.command.alloy");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        int modCount = AlloyLoader.getInstance().getLoadedMods().size();
        String environment = AlloyAPI.environment().name().toLowerCase();

        sender.sendMessage("[Alloy] Alloy Mod Loader v" + AlloyLoader.VERSION);
        sender.sendMessage("[Alloy] Minecraft: " + AlloyLoader.MINECRAFT_TARGET);
        sender.sendMessage("[Alloy] Environment: " + environment);
        sender.sendMessage("[Alloy] Loaded mods: " + modCount);

        return true;
    }
}
