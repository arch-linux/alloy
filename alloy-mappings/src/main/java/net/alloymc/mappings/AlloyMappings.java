package net.alloymc.mappings;

import net.alloymc.mappings.model.ClassMapping;
import net.alloymc.mappings.mojang.AssetDownloader;
import net.alloymc.mappings.mojang.MojangApi;
import net.alloymc.mappings.mojang.MojangApi.Download;
import net.alloymc.mappings.mojang.MojangApi.Library;
import net.alloymc.mappings.mojang.MojangApi.VersionDetails;
import net.alloymc.mappings.mojang.MojangApi.VersionEntry;
import net.alloymc.mappings.proguard.ProGuardParser;
import net.alloymc.mappings.remap.JarRemapper;
import net.alloymc.mappings.remap.MappingSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Entry point for the Alloy workspace setup pipeline.
 *
 * <p>Orchestrates everything needed to develop and launch Minecraft with Alloy:
 * <ol>
 *   <li>Fetches version metadata from Mojang</li>
 *   <li>Downloads client JAR, mappings, libraries, and assets</li>
 *   <li>Extracts native libraries for the current platform</li>
 *   <li>Parses mappings and produces a deobfuscated JAR</li>
 *   <li>Generates a launch script</li>
 * </ol>
 */
public final class AlloyMappings {

    public static void main(String[] args) {
        String requestedVersion = args.length > 0 ? args[0] : "latest";
        String cacheDirStr = args.length > 1 ? args[1] : "cache";
        String loaderJar = args.length > 2 ? args[2] : null;

        try {
            run(requestedVersion, Path.of(cacheDirStr), loaderJar);
        } catch (Exception e) {
            System.err.println("[Alloy] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Path run(String requestedVersion, Path cacheDir, String loaderJarPath)
            throws Exception {
        long startTime = System.currentTimeMillis();

        System.out.println("[Alloy] ========================================");
        System.out.println("[Alloy]  Alloy Workspace Setup");
        System.out.println("[Alloy] ========================================");
        System.out.println();

        // Step 1: Resolve version
        MojangApi api = new MojangApi();
        System.out.println("[Alloy] Resolving Minecraft version: " + requestedVersion);

        VersionEntry version;
        if ("latest".equalsIgnoreCase(requestedVersion)) {
            version = api.getLatestRelease();
        } else {
            version = api.getVersion(requestedVersion);
        }
        System.out.println("[Alloy] Target: Minecraft " + version.id()
                + " (" + version.type() + ")");

        // Step 2: Fetch download details
        System.out.println("[Alloy] Fetching version metadata...");
        VersionDetails details = api.fetchDetails(version);
        System.out.println("[Alloy] Main class: " + details.mainClass());
        System.out.println();

        Path versionDir = cacheDir.resolve(version.id());
        Files.createDirectories(versionDir);

        // Step 3: Download client JAR and mappings
        System.out.println("[Alloy] --- Client & Mappings ---");
        Path clientJar = api.download(details.client(), versionDir.resolve("client.jar"));
        System.out.println("[Alloy] client.jar: " + MojangApi.formatSize(details.client().size()));
        Path mappingsFile = api.download(details.clientMappings(),
                versionDir.resolve("client_mappings.txt"));
        System.out.println("[Alloy] client_mappings.txt: "
                + MojangApi.formatSize(details.clientMappings().size()));

        // Download server JAR if available
        // Modern MC server JARs (1.18+) are bundled — the actual server classes
        // are inside META-INF/versions/<version>/server-<version>.jar
        Path serverJar = null;
        if (details.server() != null) {
            Path bundledServerJar = api.download(details.server(), versionDir.resolve("server.jar"));
            System.out.println("[Alloy] server.jar: "
                    + MojangApi.formatSize(details.server().size()));

            // Extract the inner server JAR from the bundled JAR
            Path extractedServerJar = versionDir.resolve("server-extracted.jar");
            if (!Files.exists(extractedServerJar)) {
                System.out.println("[Alloy] Extracting inner server JAR from bundled server.jar...");
                extractInnerServerJar(bundledServerJar, version.id(), extractedServerJar);
            } else {
                System.out.println("[Alloy] server-extracted.jar already exists (cached)");
            }
            serverJar = extractedServerJar;
        } else {
            System.out.println("[Alloy] No server JAR available for this version");
        }
        System.out.println();

        // Step 4: Download libraries
        System.out.println("[Alloy] --- Libraries ---");
        List<Library> platformLibs = MojangApi.filterForPlatform(details.libraries());
        System.out.println("[Alloy] " + platformLibs.size() + " libraries for this platform"
                + " (of " + details.libraries().size() + " total)");

        Path libDir = versionDir.resolve("libraries");
        Path nativesDir = versionDir.resolve("natives");
        Files.createDirectories(libDir);
        Files.createDirectories(nativesDir);

        List<Path> libraryJars = new ArrayList<>();
        List<Path> nativeJars = new ArrayList<>();
        int libsCached = 0;

        for (Library lib : platformLibs) {
            // Use the artifact path from Maven coordinates for organized storage
            String fileName = mavenFileName(lib.name());
            Path dest = libDir.resolve(fileName);

            boolean wasCached = Files.exists(dest);
            api.download(lib.artifact(), dest);
            if (wasCached) libsCached++;

            if (lib.isNative()) {
                nativeJars.add(dest);
            } else {
                libraryJars.add(dest);
            }
        }

        System.out.println("[Alloy] Downloaded: " + (platformLibs.size() - libsCached)
                + " new, " + libsCached + " cached");

        // Step 5: Extract native libraries
        if (!nativeJars.isEmpty()) {
            System.out.println("[Alloy] Extracting " + nativeJars.size() + " native JAR(s)...");
            int extracted = 0;
            for (Path nativeJar : nativeJars) {
                extracted += extractNatives(nativeJar, nativesDir);
            }
            System.out.println("[Alloy] Extracted " + extracted + " native file(s)");
        }
        System.out.println();

        // Step 6: Download assets
        System.out.println("[Alloy] --- Assets ---");
        Path assetsDir = versionDir.resolve("assets");
        AssetDownloader.downloadAll(api, details.assetIndex(), assetsDir);
        System.out.println();

        // Step 7: Parse mappings and remap
        System.out.println("[Alloy] --- Remapping ---");
        List<ClassMapping> classes = ProGuardParser.parse(mappingsFile);
        System.out.println("[Alloy] Parsed " + classes.size() + " class mappings");

        MappingSet mappings = MappingSet.build(classes);
        System.out.println("[Alloy] Indexed: " + mappings.getClassCount() + " classes, "
                + mappings.getMethodCount() + " methods, "
                + mappings.getFieldCount() + " fields");

        Path remappedJar = versionDir.resolve("client-remapped.jar");
        if (!Files.exists(remappedJar)) {
            System.out.println("[Alloy] Remapping client.jar → client-remapped.jar");
            JarRemapper.remap(clientJar, remappedJar, mappings);
        } else {
            System.out.println("[Alloy] client-remapped.jar already exists (cached)");
        }
        System.out.println();

        // Step 8: Generate launch script
        System.out.println("[Alloy] --- Launch Script ---");
        Path launchScript = generateLaunchScript(
                versionDir, version.id(), details.mainClass(),
                details.assetIndex().id(),
                clientJar, libraryJars, nativeJars, nativesDir, assetsDir,
                loaderJarPath);
        System.out.println("[Alloy] Generated: " + launchScript.toAbsolutePath());

        // Generate server launch script if server JAR was downloaded
        if (serverJar != null) {
            Path serverLaunchScript = generateServerLaunchScript(
                    versionDir, version.id(),
                    serverJar, libraryJars, loaderJarPath);
            System.out.println("[Alloy] Generated: " + serverLaunchScript.toAbsolutePath());
        }
        System.out.println();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[Alloy] ========================================");
        System.out.println("[Alloy]  Workspace setup complete in " + formatTime(elapsed));
        System.out.println("[Alloy]  Launch: ./gradlew launchClient");
        System.out.println("[Alloy] ========================================");

        return remappedJar;
    }

    /**
     * Extracts native library files (.dylib, .dll, .so) from a JAR.
     */
    private static int extractNatives(Path nativeJar, Path nativesDir) throws IOException {
        int count = 0;
        try (JarFile jar = new JarFile(nativeJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (entry.isDirectory() || name.startsWith("META-INF")) {
                    continue;
                }

                if (name.endsWith(".dylib") || name.endsWith(".dll")
                        || name.endsWith(".so") || name.endsWith(".jnilib")) {
                    // Use just the filename, not the full path in the JAR
                    String fileName = name.contains("/")
                            ? name.substring(name.lastIndexOf('/') + 1)
                            : name;
                    Path target = nativesDir.resolve(fileName);

                    if (!Files.exists(target)) {
                        try (InputStream is = jar.getInputStream(entry)) {
                            Files.copy(is, target);
                        }
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Generates a bash launch script with the full classpath and game arguments.
     */
    private static Path generateLaunchScript(
            Path versionDir, String versionId, String mainClass,
            String assetIndexId,
            Path clientJar, List<Path> libraryJars, List<Path> nativeJars,
            Path nativesDir, Path assetsDir,
            String loaderJarPath) throws IOException {

        Path runDir = versionDir.resolve("../../run").normalize();
        Files.createDirectories(runDir);
        Files.createDirectories(runDir.resolve("mods"));

        StringBuilder cp = new StringBuilder();

        // Alloy loader JAR (if provided)
        if (loaderJarPath != null && !loaderJarPath.isBlank()) {
            cp.append(loaderJarPath).append(":");

            // Alloy API JAR — resolve relative to the loader JAR
            Path loaderPath = Path.of(loaderJarPath).toAbsolutePath().getParent();
            Path apiDir = loaderPath.resolve("../../../alloy-api/build/libs").normalize();
            if (Files.isDirectory(apiDir)) {
                try (var stream = Files.newDirectoryStream(apiDir, "alloy-api-*.jar")) {
                    for (Path apiJar : stream) {
                        cp.append(apiJar.toAbsolutePath()).append(":");
                    }
                }
            }
        }

        // Client JAR
        cp.append(clientJar.toAbsolutePath());

        // Library JARs (non-native)
        for (Path lib : libraryJars) {
            cp.append(":").append(lib.toAbsolutePath());
        }

        // Native JARs also need to be on classpath for LWJGL class loading
        for (Path nativeJar : nativeJars) {
            cp.append(":").append(nativeJar.toAbsolutePath());
        }

        // Determine the main class: use AlloyLoader if loader JAR provided, else MC directly
        String launchMainClass;
        String agentArg = "";
        String iconArg = "";
        if (loaderJarPath != null && !loaderJarPath.isBlank()) {
            launchMainClass = "net.alloymc.loader.AlloyLoader";
            agentArg = "-javaagent:\"" + loaderJarPath + "\"";

            // Resolve alloy_logo.png from alloy-client resources
            Path loaderPath = Path.of(loaderJarPath).toAbsolutePath().getParent();
            Path logoFile = loaderPath.resolve(
                    "../../../alloy-client/src/main/resources/assets/alloy/textures/alloy_logo.png")
                    .normalize();
            if (Files.exists(logoFile)) {
                iconArg = "-Dalloy.icon.path=\"" + logoFile.toAbsolutePath() + "\"";
            }
        } else {
            launchMainClass = mainClass;
        }

        String script = """
                #!/bin/bash
                # Auto-generated by Alloy setupWorkspace
                # Minecraft %s via Alloy Mod Loader

                JAVA_BIN="${JAVA_HOME:-/usr}/bin/java"
                if [ ! -f "$JAVA_BIN" ]; then
                    JAVA_BIN="java"
                fi

                # macOS requires -XstartOnFirstThread for GLFW/OpenGL
                MACOS_ARGS=""
                if [ "$(uname)" = "Darwin" ]; then
                    MACOS_ARGS="-XstartOnFirstThread"
                fi

                exec "$JAVA_BIN" \\
                  $MACOS_ARGS \\
                  %s \\
                  %s \\
                  -Xmx2G \\
                  -Xms512M \\
                  --add-modules=jdk.incubator.vector \\
                  --enable-native-access=ALL-UNNAMED \\
                  --add-opens=java.base/java.lang=ALL-UNNAMED \\
                  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \\
                  --add-opens=java.base/java.io=ALL-UNNAMED \\
                  --add-opens=java.base/java.util=ALL-UNNAMED \\
                  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \\
                  -Dorg.lwjgl.util.Debug=false \\
                  -Djava.library.path="%s" \\
                  -Dminecraft.launcher.brand=alloy \\
                  -Dminecraft.launcher.version=0.1.0 \\
                  -cp "%s" \\
                  %s \\
                  --version %s \\
                  --gameDir "%s" \\
                  --assetsDir "%s" \\
                  --assetIndex %s \\
                  --accessToken alloy_dev \\
                  --username AlloyDev \\
                  --uuid 00000000-0000-0000-0000-000000000000 \\
                  --userType msa \\
                  --versionType release \\
                  "$@"
                """.formatted(
                versionId,
                agentArg,
                iconArg,
                nativesDir.toAbsolutePath(),
                cp,
                launchMainClass,
                versionId,
                runDir.toAbsolutePath(),
                assetsDir.toAbsolutePath(),
                assetIndexId
        );

        Path launchFile = versionDir.resolve("launch.sh");
        Files.writeString(launchFile, script);

        // Make executable
        try {
            Files.setPosixFilePermissions(launchFile,
                    PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            // Windows doesn't support POSIX permissions
        }

        return launchFile;
    }

    /**
     * Extracts the inner server JAR from the modern bundled server JAR format.
     * The actual server classes live at META-INF/versions/{version}/server-{version}.jar
     */
    private static void extractInnerServerJar(Path bundledJar, String versionId, Path target)
            throws IOException {
        String innerPath = "META-INF/versions/" + versionId + "/server-" + versionId + ".jar";

        try (JarFile jar = new JarFile(bundledJar.toFile())) {
            JarEntry entry = jar.getJarEntry(innerPath);
            if (entry == null) {
                throw new IOException("Could not find inner server JAR at '" + innerPath
                        + "' inside " + bundledJar.getFileName()
                        + ". The server JAR format may have changed.");
            }

            try (InputStream is = jar.getInputStream(entry)) {
                Files.copy(is, target);
            }
            System.out.println("[Alloy] Extracted: server-extracted.jar ("
                    + MojangApi.formatSize(Files.size(target)) + ")");
        }
    }

    /**
     * Generates a bash launch script for the dedicated server.
     * No -XstartOnFirstThread, no LWJGL paths, no assets/auth args.
     */
    private static Path generateServerLaunchScript(
            Path versionDir, String versionId,
            Path serverJar, List<Path> libraryJars,
            String loaderJarPath) throws IOException {

        Path runServerDir = versionDir.resolve("../../run-server").normalize();
        Files.createDirectories(runServerDir);
        Files.createDirectories(runServerDir.resolve("mods"));

        StringBuilder cp = new StringBuilder();

        // Alloy loader JAR (if provided)
        if (loaderJarPath != null && !loaderJarPath.isBlank()) {
            cp.append(loaderJarPath).append(":");

            // Alloy API JAR
            Path loaderPath = Path.of(loaderJarPath).toAbsolutePath().getParent();
            Path apiDir = loaderPath.resolve("../../../alloy-api/build/libs").normalize();
            if (Files.isDirectory(apiDir)) {
                try (var stream = Files.newDirectoryStream(apiDir, "alloy-api-*.jar")) {
                    for (Path apiJar : stream) {
                        cp.append(apiJar.toAbsolutePath()).append(":");
                    }
                }
            }

            // Alloy Core JAR
            Path coreDir = loaderPath.resolve("../../../alloy-core/build/libs").normalize();
            if (Files.isDirectory(coreDir)) {
                try (var stream = Files.newDirectoryStream(coreDir, "AlloyCore-*.jar")) {
                    for (Path coreJar : stream) {
                        cp.append(coreJar.toAbsolutePath()).append(":");
                    }
                }
            }
        }

        // Server JAR
        cp.append(serverJar.toAbsolutePath());

        // Library JARs (non-native, server doesn't need natives)
        for (Path lib : libraryJars) {
            // Skip LWJGL libraries — server doesn't need them
            String fileName = lib.getFileName().toString().toLowerCase();
            if (fileName.contains("lwjgl") || fileName.contains("glfw")
                    || fileName.contains("openal") || fileName.contains("stb")
                    || fileName.contains("jemalloc") || fileName.contains("freetype")) {
                continue;
            }
            cp.append(":").append(lib.toAbsolutePath());
        }

        // Determine the main class
        String launchMainClass;
        String agentArg = "";
        if (loaderJarPath != null && !loaderJarPath.isBlank()) {
            launchMainClass = "net.alloymc.loader.AlloyLoader";
            agentArg = "-javaagent:\"" + loaderJarPath + "\"";
        } else {
            launchMainClass = "net.minecraft.server.Main";
        }

        String script = """
                #!/bin/bash
                # Auto-generated by Alloy setupWorkspace
                # Minecraft %s Dedicated Server via Alloy Mod Loader

                JAVA_BIN="${JAVA_HOME:-/usr}/bin/java"
                if [ ! -f "$JAVA_BIN" ]; then
                    JAVA_BIN="java"
                fi

                exec "$JAVA_BIN" \\
                  %s \\
                  -Dalloy.environment=server \\
                  -Xmx2G \\
                  -Xms512M \\
                  --add-modules=jdk.incubator.vector \\
                  --enable-native-access=ALL-UNNAMED \\
                  --add-opens=java.base/java.lang=ALL-UNNAMED \\
                  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \\
                  --add-opens=java.base/java.io=ALL-UNNAMED \\
                  --add-opens=java.base/java.util=ALL-UNNAMED \\
                  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \\
                  -Dminecraft.launcher.brand=alloy \\
                  -Dminecraft.launcher.version=0.1.0 \\
                  -cp "%s" \\
                  %s \\
                  --server \\
                  --version %s \\
                  --gameDir "%s" \\
                  nogui \\
                  "$@"
                """.formatted(
                versionId,
                agentArg,
                cp,
                launchMainClass,
                versionId,
                runServerDir.toAbsolutePath()
        );

        Path launchFile = versionDir.resolve("launch-server.sh");
        Files.writeString(launchFile, script);

        try {
            Files.setPosixFilePermissions(launchFile,
                    PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            // Windows doesn't support POSIX permissions
        }

        return launchFile;
    }

    /**
     * Converts a Maven coordinate to a filename.
     * "org.lwjgl:lwjgl:3.3.3" → "lwjgl-3.3.3.jar"
     * "org.lwjgl:lwjgl:3.3.3:natives-macos-arm64" → "lwjgl-3.3.3-natives-macos-arm64.jar"
     */
    private static String mavenFileName(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length >= 3) {
            String artifactId = parts[1];
            String version = parts[2];
            if (parts.length >= 4) {
                return artifactId + "-" + version + "-" + parts[3] + ".jar";
            }
            return artifactId + "-" + version + ".jar";
        }
        return coordinate.replace(":", "-") + ".jar";
    }

    private static String formatTime(long millis) {
        if (millis < 1000) return millis + "ms";
        return String.format("%.1fs", millis / 1000.0);
    }
}
