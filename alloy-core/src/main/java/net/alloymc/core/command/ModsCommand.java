package net.alloymc.core.command;

import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.loader.AlloyLoader;
import net.alloymc.loader.mod.LoadedMod;

import java.util.List;

/**
 * Lists all loaded mods and their versions.
 */
public class ModsCommand extends Command {

    public ModsCommand() {
        super("mods", "List loaded mods", "alloy.command.mods", List.of("plugins"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        List<LoadedMod> mods = AlloyLoader.getInstance().getLoadedMods();

        if (mods.isEmpty()) {
            sender.sendMessage("[Alloy] No mods loaded.");
            return true;
        }

        sender.sendMessage("[Alloy] Loaded mods (" + mods.size() + "):");
        for (LoadedMod mod : mods) {
            sender.sendMessage("  - " + mod.metadata().name()
                    + " v" + mod.metadata().version()
                    + " [" + mod.metadata().id() + "]");
        }

        return true;
    }
}
