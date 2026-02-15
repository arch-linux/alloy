package net.alloymc.loader.mod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans a directory for mod JARs and extracts their metadata.
 *
 * <p>A valid mod JAR must contain {@code alloy.mod.json} at its root.
 * JARs without this file are silently skipped. JARs with malformed metadata
 * produce a clear error identifying the problem JAR.
 */
public final class ModDiscovery {

    private ModDiscovery() {}

    /**
     * Discovers all mods in the given directory.
     *
     * @param modsDir the directory to scan (typically {@code .minecraft/mods/})
     * @return list of discovered mod candidates, in no particular order
     */
    public static List<ModCandidate> discover(Path modsDir) throws IOException {
        if (!Files.isDirectory(modsDir)) {
            return Collections.emptyList();
        }

        List<ModCandidate> candidates = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jarPath : stream) {
                ModCandidate candidate = tryLoadCandidate(jarPath);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        return candidates;
    }

    /**
     * Attempts to load mod metadata from a single JAR file.
     *
     * @return the mod candidate, or null if the JAR doesn't contain alloy.mod.json
     * @throws IOException if the JAR contains alloy.mod.json but it's malformed
     */
    private static ModCandidate tryLoadCandidate(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry metadataEntry = jar.getJarEntry(ModMetadata.FILENAME);

            if (metadataEntry == null) {
                // Not an Alloy mod — skip silently
                return null;
            }

            try (InputStream in = jar.getInputStream(metadataEntry)) {
                ModMetadata metadata = ModMetadata.parse(in);
                return new ModCandidate(metadata, jarPath);
            } catch (IOException e) {
                throw new IOException("Failed to read " + ModMetadata.FILENAME
                        + " from " + jarPath.getFileName() + ": " + e.getMessage(), e);
            }
        }
    }
}
