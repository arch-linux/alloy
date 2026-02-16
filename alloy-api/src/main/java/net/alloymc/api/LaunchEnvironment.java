package net.alloymc.api;

/**
 * Represents the environment in which Alloy is running.
 */
public enum LaunchEnvironment {

    CLIENT,
    SERVER;

    /**
     * Returns true if a mod with the given environment string should be loaded
     * in this environment. A mod with environment "both" loads everywhere.
     *
     * @param modEnvironment the mod's declared environment ("client", "server", or "both")
     */
    public boolean shouldLoad(String modEnvironment) {
        if ("both".equals(modEnvironment)) return true;
        return name().equalsIgnoreCase(modEnvironment);
    }
}
