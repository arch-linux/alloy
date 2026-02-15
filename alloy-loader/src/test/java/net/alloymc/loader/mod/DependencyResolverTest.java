package net.alloymc.loader.mod;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyResolverTest {

    private static ModCandidate makeMod(String id, String version, Map<String, String> deps) {
        ModMetadata meta = new ModMetadata(
                id, id, version, "", List.of(), "", "", deps, "both");
        return new ModCandidate(meta, Path.of("mods/" + id + ".jar"));
    }

    @Test
    void resolvesEmptyList() {
        List<ModCandidate> result = DependencyResolver.resolve(
                List.of(), "1.21.4", "0.1.0");
        assertEquals(0, result.size());
    }

    @Test
    void resolvesIndependentMods() {
        List<ModCandidate> candidates = List.of(
                makeMod("mod-b", "1.0.0", Map.of()),
                makeMod("mod-a", "1.0.0", Map.of())
        );
        List<ModCandidate> result = DependencyResolver.resolve(
                candidates, "1.21.4", "0.1.0");
        // Alphabetical order for independent mods
        assertEquals("mod-a", result.get(0).id());
        assertEquals("mod-b", result.get(1).id());
    }

    @Test
    void respectsDependencyOrder() {
        List<ModCandidate> candidates = List.of(
                makeMod("mod-b", "1.0.0", Map.of("mod-a", ">=1.0.0")),
                makeMod("mod-a", "1.0.0", Map.of())
        );
        List<ModCandidate> result = DependencyResolver.resolve(
                candidates, "1.21.4", "0.1.0");
        assertEquals("mod-a", result.get(0).id()); // dependency first
        assertEquals("mod-b", result.get(1).id());
    }

    @Test
    void checksMinecraftVersion() {
        List<ModCandidate> candidates = List.of(
                makeMod("my-mod", "1.0.0", Map.of("minecraft", ">=1.22.0"))
        );
        DependencyResolver.DependencyException e = assertThrows(
                DependencyResolver.DependencyException.class,
                () -> DependencyResolver.resolve(candidates, "1.21.4", "0.1.0")
        );
        assertTrue(e.getMessage().contains("minecraft"));
        assertTrue(e.getMessage().contains(">=1.22.0"));
    }

    @Test
    void checksAlloyVersion() {
        List<ModCandidate> candidates = List.of(
                makeMod("my-mod", "1.0.0", Map.of("alloy", ">=1.0.0"))
        );
        DependencyResolver.DependencyException e = assertThrows(
                DependencyResolver.DependencyException.class,
                () -> DependencyResolver.resolve(candidates, "1.21.4", "0.1.0")
        );
        assertTrue(e.getMessage().contains("alloy"));
    }

    @Test
    void detectsMissingDependency() {
        List<ModCandidate> candidates = List.of(
                makeMod("my-mod", "1.0.0", Map.of("missing-lib", ">=1.0.0"))
        );
        DependencyResolver.DependencyException e = assertThrows(
                DependencyResolver.DependencyException.class,
                () -> DependencyResolver.resolve(candidates, "1.21.4", "0.1.0")
        );
        assertTrue(e.getMessage().contains("missing-lib"));
        assertTrue(e.getMessage().contains("not installed"));
    }

    @Test
    void detectsDuplicateModIds() {
        List<ModCandidate> candidates = List.of(
                makeMod("dupe", "1.0.0", Map.of()),
                makeMod("dupe", "2.0.0", Map.of())
        );
        DependencyResolver.DependencyException e = assertThrows(
                DependencyResolver.DependencyException.class,
                () -> DependencyResolver.resolve(candidates, "1.21.4", "0.1.0")
        );
        assertTrue(e.getMessage().contains("Duplicate"));
    }

    @Test
    void detectsCircularDependency() {
        List<ModCandidate> candidates = List.of(
                makeMod("mod-a", "1.0.0", Map.of("mod-b", "*")),
                makeMod("mod-b", "1.0.0", Map.of("mod-a", "*"))
        );
        DependencyResolver.DependencyException e = assertThrows(
                DependencyResolver.DependencyException.class,
                () -> DependencyResolver.resolve(candidates, "1.21.4", "0.1.0")
        );
        assertTrue(e.getMessage().contains("Circular"));
    }

    @Test
    void versionConstraints() {
        assertTrue(DependencyResolver.satisfiesConstraint("1.21.4", "*"));
        assertTrue(DependencyResolver.satisfiesConstraint("1.21.4", ">=1.21.0"));
        assertTrue(DependencyResolver.satisfiesConstraint("1.21.4", ">=1.21.4"));
        assertTrue(DependencyResolver.satisfiesConstraint("2.0.0", ">1.21.4"));
        assertTrue(DependencyResolver.satisfiesConstraint("1.0.0", "<=1.0.0"));
        assertTrue(DependencyResolver.satisfiesConstraint("0.9.0", "<1.0.0"));
        assertTrue(DependencyResolver.satisfiesConstraint("1.21.4", "1.21.4"));

        assertTrue(!DependencyResolver.satisfiesConstraint("1.20.0", ">=1.21.0"));
        assertTrue(!DependencyResolver.satisfiesConstraint("1.21.4", ">1.21.4"));
        assertTrue(!DependencyResolver.satisfiesConstraint("2.0.0", "<1.0.0"));
    }

    @Test
    void complexDependencyChain() {
        // d depends on c, c depends on b, b depends on a
        List<ModCandidate> candidates = List.of(
                makeMod("mod-d", "1.0.0", Map.of("mod-c", ">=1.0.0")),
                makeMod("mod-b", "1.0.0", Map.of("mod-a", ">=1.0.0")),
                makeMod("mod-c", "1.0.0", Map.of("mod-b", ">=1.0.0")),
                makeMod("mod-a", "1.0.0", Map.of())
        );
        List<ModCandidate> result = DependencyResolver.resolve(
                candidates, "1.21.4", "0.1.0");
        assertEquals("mod-a", result.get(0).id());
        assertEquals("mod-b", result.get(1).id());
        assertEquals("mod-c", result.get(2).id());
        assertEquals("mod-d", result.get(3).id());
    }
}
