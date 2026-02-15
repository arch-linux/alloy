package net.alloymc.mod.griefprevention.task;

import net.alloymc.mod.griefprevention.GriefPreventionMod;

import java.util.logging.Logger;

/**
 * Periodically saves all data to disk as a safety measure.
 * Runs every 5 minutes.
 */
public class DataSaveTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger("GriefPrevention.Save");
    private final GriefPreventionMod mod;

    public DataSaveTask(GriefPreventionMod mod) {
        this.mod = mod;
    }

    @Override
    public void run() {
        mod.dataStore().saveAll(mod.claimManager());
        LOGGER.fine("Auto-saved all GriefPrevention data.");
    }
}
