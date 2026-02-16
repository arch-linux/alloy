package net.alloymc.loader.launch;

import java.lang.reflect.Method;

/**
 * Launches the Minecraft dedicated server main class through a given classloader.
 *
 * <p>Mirror of {@link GameLauncher} but targets the server entry point.
 */
public final class ServerLauncher {

    private ServerLauncher() {}

    /**
     * Loads and invokes the Minecraft dedicated server main class.
     *
     * @param classLoader the classloader to use (should have Minecraft server classes available)
     * @param gameArgs    the original command-line arguments to pass to the server
     */
    public static void launch(ClassLoader classLoader, String[] gameArgs) {
        try {
            Class<?> mainClass = Class.forName(LaunchTarget.SERVER.mainClass(), true, classLoader);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) gameArgs);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find Minecraft server main class '"
                    + LaunchTarget.SERVER.mainClass()
                    + "'. Ensure the Minecraft server JAR is on the classpath. "
                    + "Run './gradlew setupWorkspace' to download the server JAR.", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Minecraft server main class exists but has no main() method. "
                    + "This is unexpected — the Minecraft version may be incompatible.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch Minecraft server: " + e.getMessage(), e);
        }
    }
}
