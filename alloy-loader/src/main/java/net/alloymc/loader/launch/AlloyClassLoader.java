package net.alloymc.loader.launch;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Custom classloader for the Alloy mod loading environment.
 *
 * <p>Phase 1: Extends URLClassLoader to aggregate mod JARs. Mods are loaded through
 * this classloader and can access parent classpath classes (Minecraft, libraries)
 * via standard delegation.
 *
 * <p>Phase 2 will add bytecode transformation support — intercepting class loading
 * to apply ASM-based modifications before classes are defined.
 */
public final class AlloyClassLoader extends URLClassLoader {

    static {
        // Enable parallel class loading for better startup performance
        ClassLoader.registerAsParallelCapable();
    }

    public AlloyClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    /**
     * Adds a mod JAR to this classloader's search path.
     *
     * @param jarPath path to a mod JAR file
     * @throws RuntimeException if the path cannot be converted to a URL
     */
    public void addJar(Path jarPath) {
        try {
            addURL(jarPath.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to add JAR to classloader: " + jarPath
                    + ". The file path may contain invalid characters.", e);
        }
    }

    @Override
    public String toString() {
        return "AlloyClassLoader[" + getURLs().length + " JARs]";
    }
}
