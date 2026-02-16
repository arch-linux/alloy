package net.alloymc.loader;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.LaunchEnvironment;
import net.alloymc.api.command.CommandRegistry;
import net.alloymc.api.event.EventBus;
import net.alloymc.api.permission.PermissionRegistry;
import net.alloymc.loader.api.ModInitializer;
import net.alloymc.loader.impl.StubScheduler;
import net.alloymc.loader.impl.StubServer;
import net.alloymc.loader.launch.AlloyClassLoader;
import net.alloymc.loader.launch.GameLauncher;
import net.alloymc.loader.launch.ServerLauncher;
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

    /**
     * Called by the Java agent ({@link net.alloymc.loader.agent.AlloyAgent}) during
     * premain to initialize the Alloy API and discover/load mods, without launching
     * Minecraft (since MC is already being launched via {@code -jar server.jar}).
     */
    public static void bootstrap() {
        long startTime = System.currentTimeMillis();
        LaunchEnvironment environment = LaunchEnvironment.SERVER;

        String envProp = System.getProperty("alloy.environment");
        if ("client".equalsIgnoreCase(envProp)) {
            environment = LaunchEnvironment.CLIENT;
        }

        System.out.println();
        System.out.println("[Alloy] ========================================");
        System.out.println("[Alloy]  Alloy Mod Loader v" + VERSION);
        System.out.println("[Alloy]  Environment: " + environment.name());
        System.out.println("[Alloy] ========================================");
        System.out.println();

        try {
            Path gameDir = Path.of(
                    System.getProperty("alloy.gameDir",
                            System.getProperty("user.dir")));
            Path modsDir = gameDir.resolve("mods");

            System.out.println("[Alloy] Game directory: " + gameDir.toAbsolutePath());
            System.out.println("[Alloy] Mods directory: " + modsDir.toAbsolutePath());

            if (!Files.isDirectory(modsDir)) {
                Files.createDirectories(modsDir);
                System.out.println("[Alloy] Created mods directory");
            }

            // Discover mods
            System.out.println();
            System.out.println("[Alloy] Scanning for mods...");
            List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
            System.out.println("[Alloy] Discovered " + candidates.size() + " mod(s)");

            // Filter by environment
            final LaunchEnvironment env = environment;
            List<ModCandidate> filtered = candidates.stream()
                    .filter(mod -> env.shouldLoad(mod.metadata().environment()))
                    .toList();

            // Resolve dependencies
            List<ModCandidate> sorted = DependencyResolver.resolve(
                    filtered, MINECRAFT_TARGET, VERSION);

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
                    ClassLoader.getSystemClassLoader());
            for (ModCandidate mod : sorted) {
                modClassLoader.addJar(mod.jarPath());
            }

            // Bootstrap the Alloy API
            AlloyAPI.initialize(
                    new StubServer(gameDir),
                    new EventBus(),
                    new CommandRegistry(),
                    new PermissionRegistry(),
                    new StubScheduler(),
                    environment
            );
            System.out.println("[Alloy] API initialized");

            // Load and initialize mods
            System.out.println();
            List<LoadedMod> loaded = initializeMods(sorted, modClassLoader);

            instance = new AlloyLoader(loaded, modClassLoader);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println();
            System.out.println("[Alloy] " + loaded.size() + " mod(s) loaded in "
                    + elapsed + "ms");
            System.out.println();

        } catch (DependencyResolver.DependencyException e) {
            System.err.println("[Alloy] DEPENDENCY ERROR: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Alloy] FATAL ERROR during bootstrap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // Detect launch environment
        LaunchEnvironment environment = detectLaunchEnvironment(args);

        System.out.println();
        System.out.println("[Alloy] ========================================");
        System.out.println("[Alloy]  Alloy Mod Loader v" + VERSION);
        System.out.println("[Alloy]  Environment: " + environment.name());
        System.out.println("[Alloy] ========================================");
        System.out.println();

        try {
            // Find game directory — server defaults to current working directory
            Path gameDir = (environment == LaunchEnvironment.SERVER)
                    ? findServerDir(args)
                    : findGameDir(args);
            Path modsDir = gameDir.resolve("mods");

            // Store for EventFiringHook.onServerReady() to pick up
            System.setProperty("alloy.gameDir", gameDir.toAbsolutePath().toString());
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

            // Filter by environment
            List<ModCandidate> filtered = candidates.stream()
                    .filter(mod -> environment.shouldLoad(mod.metadata().environment()))
                    .toList();

            if (filtered.size() < candidates.size()) {
                int skipped = candidates.size() - filtered.size();
                System.out.println("[Alloy] Filtered out " + skipped
                        + " mod(s) not compatible with " + environment.name());
            }

            // Resolve dependencies
            String mcVersion = detectMinecraftVersion(args);
            List<ModCandidate> sorted = DependencyResolver.resolve(
                    filtered, mcVersion, VERSION);

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
                    ClassLoader.getSystemClassLoader());
            for (ModCandidate mod : sorted) {
                modClassLoader.addJar(mod.jarPath());
            }

            // Bootstrap the Alloy API so mods can use it during onInitialize()
            AlloyAPI.initialize(
                    new StubServer(gameDir),
                    new EventBus(),
                    new CommandRegistry(),
                    new PermissionRegistry(),
                    new StubScheduler(),
                    environment
            );
            System.out.println("[Alloy] API initialized");

            // Load and initialize mods
            System.out.println();
            List<LoadedMod> loaded = initializeMods(sorted, modClassLoader);

            // Store instance
            instance = new AlloyLoader(loaded, modClassLoader);

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println();
            System.out.println("[Alloy] " + loaded.size() + " mod(s) loaded in "
                    + elapsed + "ms");

            // Launch the appropriate target
            if (environment == LaunchEnvironment.SERVER) {
                System.out.println("[Alloy] Launching Minecraft Server " + mcVersion + "...");
                System.out.println();
                // Strip Alloy/client-specific args that the MC server doesn't understand
                String[] serverArgs = filterServerArgs(args);
                ServerLauncher.launch(modClassLoader, serverArgs);
            } else {
                System.out.println("[Alloy] Launching Minecraft " + mcVersion + "...");
                System.out.println();
                GameLauncher.launch(modClassLoader, args);
            }

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
     * Filters out Alloy/client-specific arguments that the MC dedicated server
     * doesn't recognize. Keeps only server-compatible args (e.g., nogui).
     */
    private static String[] filterServerArgs(String[] args) {
        // Args with a following value that the server doesn't understand
        var skipWithValue = java.util.Set.of("--gameDir", "--version", "--assetsDir",
                "--assetIndex", "--accessToken", "--username", "--uuid",
                "--userType", "--versionType");
        // Standalone args to skip
        var skipStandalone = java.util.Set.of("--server");

        List<String> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (skipStandalone.contains(args[i])) {
                continue;
            }
            if (skipWithValue.contains(args[i])) {
                i++; // skip the value too
                continue;
            }
            filtered.add(args[i]);
        }
        return filtered.toArray(new String[0]);
    }

    /**
     * Detects the launch environment from command-line args or system properties.
     * Checks for --server flag or -Dalloy.environment=server.
     */
    private static LaunchEnvironment detectLaunchEnvironment(String[] args) {
        // Check system property
        String envProp = System.getProperty("alloy.environment");
        if ("server".equalsIgnoreCase(envProp)) {
            return LaunchEnvironment.SERVER;
        }

        // Check command-line flag
        for (String arg : args) {
            if ("--server".equals(arg)) {
                return LaunchEnvironment.SERVER;
            }
        }

        return LaunchEnvironment.CLIENT;
    }

    /**
     * Finds the server directory from command-line args or defaults to CWD.
     */
    private static Path findServerDir(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gameDir".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        // Server defaults to current working directory
        return Path.of("").toAbsolutePath();
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
