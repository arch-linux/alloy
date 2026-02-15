package net.alloymc.loader.mod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDiscoveryTest {

    private static final String VALID_MOD_JSON = """
            {
              "id": "test-mod",
              "name": "Test Mod",
              "version": "1.0.0",
              "description": "A test mod",
              "authors": ["Tester"],
              "license": "MIT",
              "entrypoint": "com.example.TestMod",
              "dependencies": {
                "minecraft": ">=1.21.0",
                "alloy": ">=0.1.0"
              },
              "environment": "both"
            }
            """;

    @Test
    void discoversModJar(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectory(modsDir);

        createModJar(modsDir.resolve("test-mod.jar"), VALID_MOD_JSON);

        List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
        assertEquals(1, candidates.size());
        assertEquals("test-mod", candidates.get(0).id());
        assertEquals("Test Mod", candidates.get(0).name());
    }

    @Test
    void skipsJarsWithoutMetadata(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectory(modsDir);

        // Create a JAR without alloy.mod.json
        createModJar(modsDir.resolve("not-a-mod.jar"), null);

        List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
        assertEquals(0, candidates.size());
    }

    @Test
    void skipsNonJarFiles(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectory(modsDir);

        Files.writeString(modsDir.resolve("readme.txt"), "not a mod");

        List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
        assertEquals(0, candidates.size());
    }

    @Test
    void returnsEmptyForMissingDirectory(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("nonexistent");
        List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
        assertEquals(0, candidates.size());
    }

    @Test
    void discoversMultipleMods(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectory(modsDir);

        String mod1 = VALID_MOD_JSON;
        String mod2 = VALID_MOD_JSON.replace("test-mod", "other-mod")
                .replace("Test Mod", "Other Mod");

        createModJar(modsDir.resolve("test-mod.jar"), mod1);
        createModJar(modsDir.resolve("other-mod.jar"), mod2);

        List<ModCandidate> candidates = ModDiscovery.discover(modsDir);
        assertEquals(2, candidates.size());
    }

    @Test
    void rejectsMalformedMetadata(@TempDir Path tempDir) throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectory(modsDir);

        createModJar(modsDir.resolve("bad-mod.jar"), "{ invalid json");

        assertThrows(IOException.class, () -> ModDiscovery.discover(modsDir));
    }

    @Test
    void parsesModMetadata() throws Exception {
        ModMetadata meta = ModMetadata.parse(
                new ByteArrayInputStream(VALID_MOD_JSON.getBytes(StandardCharsets.UTF_8)));

        assertEquals("test-mod", meta.id());
        assertEquals("Test Mod", meta.name());
        assertEquals("1.0.0", meta.version());
        assertEquals("A test mod", meta.description());
        assertEquals(List.of("Tester"), meta.authors());
        assertEquals("MIT", meta.license());
        assertEquals("com.example.TestMod", meta.entrypoint());
        assertEquals(">=1.21.0", meta.dependencies().get("minecraft"));
        assertEquals(">=0.1.0", meta.dependencies().get("alloy"));
        assertEquals("both", meta.environment());
    }

    @Test
    void rejectsInvalidModId() {
        String json = VALID_MOD_JSON.replace("test-mod", "INVALID_ID!");
        assertThrows(IOException.class, () -> ModMetadata.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsMissingRequiredFields() {
        String json = """
                { "id": "test" }
                """;
        assertThrows(IOException.class, () -> ModMetadata.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsInvalidEnvironment() {
        String json = VALID_MOD_JSON.replace("\"both\"", "\"invalid\"");
        assertThrows(IOException.class, () -> ModMetadata.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Creates a JAR file, optionally with alloy.mod.json at the root.
     */
    private static void createModJar(Path path, String modJson) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            // Add a dummy class entry so the JAR isn't empty
            jar.putNextEntry(new JarEntry("com/example/Dummy.class"));
            jar.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
            jar.closeEntry();

            if (modJson != null) {
                jar.putNextEntry(new JarEntry(ModMetadata.FILENAME));
                jar.write(modJson.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
        }
    }
}
