package net.alloymc.loader.launch;

import java.lang.reflect.Method;

/**
 * Launches Minecraft's main class through a given classloader.
 *
 * <p>This is the handoff point where Alloy finishes its initialization and
 * passes control to Minecraft. The game's main class is loaded through the
 * provided classloader so it participates in the mod class hierarchy.
 */
public final class GameLauncher {

    private static final String MINECRAFT_MAIN = "net.minecraft.client.main.Main";

    private GameLauncher() {}

    /**
     * Loads and invokes Minecraft's main class.
     *
     * @param classLoader the classloader to use (should have Minecraft classes available)
     * @param gameArgs    the original command-line arguments to pass to Minecraft
     */
    public static void launch(ClassLoader classLoader, String[] gameArgs) {
        try {
            Class<?> mainClass = Class.forName(MINECRAFT_MAIN, true, classLoader);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) gameArgs);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find Minecraft main class '" + MINECRAFT_MAIN
                    + "'. Ensure the Minecraft client JAR is on the classpath. "
                    + "If you're using the vanilla launcher, check your Alloy profile setup.", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Minecraft main class exists but has no main() method. "
                    + "This is unexpected — the Minecraft version may be incompatible.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch Minecraft: " + e.getMessage(), e);
        }
    }
}
