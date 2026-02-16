package net.alloymc.loader.launch;

/**
 * Identifies the Minecraft main class for each launch environment.
 */
public enum LaunchTarget {

    CLIENT("net.minecraft.client.main.Main"),
    SERVER("net.minecraft.server.Main");

    private final String mainClass;

    LaunchTarget(String mainClass) {
        this.mainClass = mainClass;
    }

    public String mainClass() {
        return mainClass;
    }
}
