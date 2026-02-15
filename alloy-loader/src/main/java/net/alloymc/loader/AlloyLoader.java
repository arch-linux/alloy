package net.alloymc.loader;

import net.alloymc.loader.api.ModInitializer;
import net.alloymc.loader.launch.AlloyClassLoader;
import net.alloymc.loader.launch.GameLauncher;
import net.alloymc.loader.mod.DependencyResolver;
import net.alloymc.loader.mod.LoadedMod;
import net.alloymc.loader.mod.ModCandidate;
import net.alloymc.loader.mod.ModDiscovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Alloy mod loader. Discovers, validates, loads, and initializes mods,
 * then hands off to Minecraft.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Locate the game directory and mods folder</li>
 *   <li>Scan for mod JARs containing {@code alloy.mod.json}</li>
 *   <li>Parse metadata, validate dependencies, determine load order</li>
 *   <li>Create a classloader with all mod JARs</li>
 *   <li>Load each mod's entrypoint and call {@code onInitialize()}</li>
 *   <li>Launch Minecraft</li>
 * </ol>
 *
 * <p>Access the loader instance at any time via {@link #getInstance()}.
 */
public final class AlloyLoader {

    public static final String VERSION = "0.1.0";
    public static final String MINECRAFT_TARGET = "1.21";

    private static AlloyLoader instance;

    private final List<LoadedMod> loadedMods;
    private final AlloyClassLoader classLoader;

    private AlloyLoader(List<LoadedMod> loadedMods, AlloyClassLoader classLoader) {
        this.loadedMods = Collections.unmodifiableList(loadedMods);
        this.classLoader = classLoader;
    }

    /** Returns the loader instance. Only available after initialization. */
    public static AlloyLoader getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AlloyLoader has not been initialized yet. "
                    + "This method can only be called during or after mod initialization.");
        }
        return instance;
    }

    /** Returns all loaded mods in their initialization order. */
    public List<LoadedMod> getLoadedMods() {
        return loadedMods;
    }

    /** Returns the mod classloader. */
    public AlloyClassLoader getClassLoader() {
        return classLoader;
    }

    // ------------------------------------------------------------------
    // Bootstrap
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("[Alloy] ========================================");
        System.out.println("[Alloy]  Alloy Mod Loader v" + VERSION);
        System.out.println("[Alloy] ========================================");
        System.out.println();

        try {
            // Find game directory
            Path gameDir = findGameDir(args);
            Path modsDir = gameDir.resolve("mods");

            System.out.println("[Alloy] Game directory: " + gameDir.toAbsolutePath());
            System.out.println("[Alloy] Mods directory: " + modsDir.toAbsolutePath());

            // Create mods directory if it doesn't exist
            if (!Files.isDirectory(modsDir)) {
                Files.createDirectories(modsDir);
                System.out.println("[Alloy] Created mods directory");
            }

            // Discover mods
            System.out.println();
            System.out.println("[Alloy] Scanning for mods...");
            List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
            System.out.println("[Alloy] Discovered " + candidates.size() + " mod(s)");

            // Resolve dependencies
            String mcVersion = detectMinecraftVersion(args);
            List<ModCandidate> sorted = DependencyResolver.resolve(
                    candidates, mcVersion, VERSION);

            if (!sorted.isEmpty()) {
                System.out.println("[Alloy] Load order:");
                for (int i = 0; i < sorted.size(); i++) {
                    ModCandidate mod = sorted.get(i);
                    System.out.println("[Alloy]   " + (i + 1) + ". " + mod.name()
                            + " v" + mod.metadata().version());
                }
            }

            // Build classloader with mod JARs
            AlloyClassLoader modClassLoader = new AlloyClassLoader(
                    AlloyLoader.class.getClassLoader());
            for (ModCandidate mod : sorted) {
                modClassLoader.addJar(mod.jarPath());
            }

            // Load and initialize mods
            System.out.println();
            List<LoadedMod> loaded = initializeMods(sorted, modClassLoader);

            // Store instance
            instance = new AlloyLoader(loaded, modClassLoader);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println();
            System.out.println("[Alloy] " + loaded.size() + " mod(s) loaded in "
                    + elapsed + "ms");
            System.out.println("[Alloy] Launching Minecraft " + mcVersion + "...");
            System.out.println();

            // Launch Minecraft
            GameLauncher.launch(modClassLoader, args);

        } catch (DependencyResolver.DependencyException e) {
            System.err.println();
            System.err.println("[Alloy] DEPENDENCY ERROR:");
            System.err.println("[Alloy] " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println();
            System.err.println("[Alloy] FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Loads each mod's entrypoint class and calls onInitialize().
     */
    private static List<LoadedMod> initializeMods(List<ModCandidate> sorted,
                                                    AlloyClassLoader classLoader) {
        List<LoadedMod> loaded = new ArrayList<>();

        for (ModCandidate mod : sorted) {
            String entrypoint = mod.metadata().entrypoint();

            if (entrypoint == null || entrypoint.isBlank()) {
                // No entrypoint — library mod, still counts as loaded
                loaded.add(new LoadedMod(mod.metadata(), mod.jarPath(), null));
                System.out.println("[Alloy] Loaded: " + mod.name()
                        + " v" + mod.metadata().version() + " (no entrypoint)");
                continue;
            }

            try {
                Class<?> entrypointClass = Class.forName(entrypoint, true, classLoader);

                if (!ModInitializer.class.isAssignableFrom(entrypointClass)) {
                    throw new RuntimeException("Entrypoint class '" + entrypoint
                            + "' in mod '" + mod.id() + "' does not implement ModInitializer. "
                            + "The entrypoint class must implement "
                            + "net.alloymc.loader.api.ModInitializer.");
                }

                ModInitializer initializer = (ModInitializer) entrypointClass
                        .getDeclaredConstructor()
                        .newInstance();

                System.out.println("[Alloy] Initializing: " + mod.name() + "...");
                initializer.onInitialize();

                loaded.add(new LoadedMod(mod.metadata(), mod.jarPath(), initializer));
                System.out.println("[Alloy] Initialized: " + mod.name()
                        + " v" + mod.metadata().version());

            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Entrypoint class '" + entrypoint
                        + "' not found in mod '" + mod.id() + "' ("
                        + mod.jarPath().getFileName() + "). "
                        + "Check the 'entrypoint' field in alloy.mod.json.", e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Entrypoint class '" + entrypoint
                        + "' in mod '" + mod.id() + "' has no public no-arg constructor. "
                        + "Add a public constructor with no parameters.", e);
            } catch (Exception e) {
                System.err.println();
                System.err.println("[Alloy] ERROR: Mod '" + mod.id() + "' failed to initialize:");
                System.err.println("[Alloy]   " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.err);
                System.err.println("[Alloy] Skipping mod '" + mod.id() + "' — the game will continue without it.");
                System.err.println();
            }
        }

        return loaded;
    }

    /**
     * Finds the game directory from command-line args or OS defaults.
     */
    private static Path findGameDir(String[] args) {
        // Check for --gameDir argument (used by Minecraft launcher)
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }

        // Auto-detect from OS
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", "minecraft");
        } else if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                return Path.of(appdata, ".minecraft");
            }
            return Path.of(home, "AppData", "Roaming", ".minecraft");
        } else {
            return Path.of(home, ".minecraft");
        }
    }

    /**
     * Detects the Minecraft version from command-line args or defaults.
     */
    private static String detectMinecraftVersion(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--version".equals(args[i])) {
                return args[i + 1];
            }
        }
        return MINECRAFT_TARGET;
    }
}
