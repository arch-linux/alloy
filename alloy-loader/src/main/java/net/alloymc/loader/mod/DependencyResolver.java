package net.alloymc.loader.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Validates mod dependencies and produces a load order via topological sort.
 *
 * <p>Checks:
 * <ul>
 *   <li>All declared mod dependencies are present</li>
 *   <li>Version constraints for mods, Minecraft, and Alloy are satisfied</li>
 *   <li>No circular dependencies exist</li>
 * </ul>
 *
 * <p>Produces a deterministic load order where dependencies are always loaded
 * before the mods that depend on them.
 */
public final class DependencyResolver {

    /** Built-in dependencies that are not mods. */
    private static final Set<String> BUILTIN_IDS = Set.of("minecraft", "alloy");

    private DependencyResolver() {}

    /**
     * Resolves dependencies and returns mods in load order.
     *
     * @param candidates      discovered mods
     * @param minecraftVersion current Minecraft version string (e.g., "1.21.4")
     * @param alloyVersion     current Alloy loader version string (e.g., "0.1.0")
     * @return mods sorted in dependency order (dependencies first)
     * @throws DependencyException if dependencies are missing, version-incompatible, or circular
     */
    public static List<ModCandidate> resolve(List<ModCandidate> candidates,
                                              String minecraftVersion,
                                              String alloyVersion)
            throws DependencyException {

        // Build lookup: mod id → candidate
        Map<String, ModCandidate> byId = new HashMap<>();
        for (ModCandidate candidate : candidates) {
            ModCandidate existing = byId.put(candidate.id(), candidate);
            if (existing != null) {
                throw new DependencyException("Duplicate mod ID '" + candidate.id() + "' — "
                        + "found in both " + existing.jarPath().getFileName()
                        + " and " + candidate.jarPath().getFileName() + ". "
                        + "Remove one of the duplicate JARs from the mods directory.");
            }
        }

        // Built-in version map
        Map<String, String> builtinVersions = Map.of(
                "minecraft", minecraftVersion,
                "alloy", alloyVersion
        );

        // Validate all dependencies
        List<String> errors = new ArrayList<>();

        for (ModCandidate candidate : candidates) {
            for (Map.Entry<String, String> dep : candidate.metadata().dependencies().entrySet()) {
                String depId = dep.getKey();
                String constraint = dep.getValue();

                if (BUILTIN_IDS.contains(depId)) {
                    // Check against built-in version
                    String actualVersion = builtinVersions.get(depId);
                    if (!satisfiesConstraint(actualVersion, constraint)) {
                        errors.add(candidate.id() + " requires " + depId + " " + constraint
                                + " but found " + actualVersion);
                    }
                } else {
                    // Check against other mods
                    ModCandidate depMod = byId.get(depId);
                    if (depMod == null) {
                        errors.add(candidate.id() + " requires mod '" + depId + "' " + constraint
                                + " but it is not installed. "
                                + "Add the missing mod to the mods directory.");
                    } else if (!satisfiesConstraint(depMod.metadata().version(), constraint)) {
                        errors.add(candidate.id() + " requires " + depId + " " + constraint
                                + " but found " + depMod.metadata().version());
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new DependencyException(
                    "Dependency errors (" + errors.size() + "):\n  - "
                            + String.join("\n  - ", errors));
        }

        // Topological sort (Kahn's algorithm)
        return topologicalSort(candidates, byId);
    }

    /**
     * Topological sort using Kahn's algorithm. Produces a deterministic order
     * (alphabetical among mods with equal priority) for reproducible builds.
     */
    private static List<ModCandidate> topologicalSort(List<ModCandidate> candidates,
                                                       Map<String, ModCandidate> byId) {
        // Build adjacency: for each mod, which mods must come before it?
        Map<String, Set<String>> incomingEdges = new HashMap<>();
        Map<String, Set<String>> outgoingEdges = new HashMap<>();

        for (ModCandidate candidate : candidates) {
            incomingEdges.putIfAbsent(candidate.id(), new HashSet<>());
            outgoingEdges.putIfAbsent(candidate.id(), new HashSet<>());
        }

        for (ModCandidate candidate : candidates) {
            for (String depId : candidate.metadata().dependencies().keySet()) {
                if (!BUILTIN_IDS.contains(depId) && byId.containsKey(depId)) {
                    // depId must come before candidate
                    incomingEdges.get(candidate.id()).add(depId);
                    outgoingEdges.get(depId).add(candidate.id());
                }
            }
        }

        // Find all nodes with no incoming edges
        Queue<String> ready = new LinkedList<>();
        for (ModCandidate candidate : candidates) {
            if (incomingEdges.get(candidate.id()).isEmpty()) {
                ready.add(candidate.id());
            }
        }

        // Sort the ready queue alphabetically for determinism
        List<String> readyList = new ArrayList<>(ready);
        readyList.sort(String::compareTo);
        ready = new LinkedList<>(readyList);

        List<ModCandidate> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        while (!ready.isEmpty()) {
            String current = ready.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            sorted.add(byId.get(current));

            // Remove this node's outgoing edges
            List<String> newlyReady = new ArrayList<>();
            for (String dependent : outgoingEdges.getOrDefault(current, Set.of())) {
                incomingEdges.get(dependent).remove(current);
                if (incomingEdges.get(dependent).isEmpty() && !visited.contains(dependent)) {
                    newlyReady.add(dependent);
                }
            }
            // Sort for determinism
            newlyReady.sort(String::compareTo);
            ready.addAll(newlyReady);
        }

        // Detect cycles
        if (sorted.size() != candidates.size()) {
            Set<String> cycled = new HashSet<>();
            for (ModCandidate candidate : candidates) {
                if (!visited.contains(candidate.id())) {
                    cycled.add(candidate.id());
                }
            }
            throw new DependencyException("Circular dependency detected involving mods: "
                    + String.join(", ", cycled) + ". "
                    + "Break the cycle by removing one of the circular dependencies.");
        }

        return sorted;
    }

    /**
     * Checks if an actual version satisfies a version constraint.
     *
     * <p>Supported constraints:
     * <ul>
     *   <li>{@code *} — any version</li>
     *   <li>{@code >=1.0.0} — at least this version</li>
     *   <li>{@code >1.0.0} — greater than this version</li>
     *   <li>{@code <=1.0.0} — at most this version</li>
     *   <li>{@code <1.0.0} — less than this version</li>
     *   <li>{@code 1.0.0} — exactly this version</li>
     * </ul>
     */
    static boolean satisfiesConstraint(String actualVersion, String constraint) {
        String trimmed = constraint.trim();
        if ("*".equals(trimmed)) return true;

        SemanticVersion actual = SemanticVersion.parse(actualVersion);
        String op;
        String versionPart;

        if (trimmed.startsWith(">=")) {
            op = ">=";
            versionPart = trimmed.substring(2);
        } else if (trimmed.startsWith(">")) {
            op = ">";
            versionPart = trimmed.substring(1);
        } else if (trimmed.startsWith("<=")) {
            op = "<=";
            versionPart = trimmed.substring(2);
        } else if (trimmed.startsWith("<")) {
            op = "<";
            versionPart = trimmed.substring(1);
        } else {
            op = "=";
            versionPart = trimmed;
        }

        SemanticVersion required = SemanticVersion.parse(versionPart);
        int cmp = actual.compareTo(required);

        return switch (op) {
            case ">=" -> cmp >= 0;
            case ">"  -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<"  -> cmp < 0;
            case "="  -> cmp == 0;
            default   -> cmp == 0;
        };
    }

    /**
     * Thrown when dependency resolution fails. The message always explains
     * what went wrong and what the user should do to fix it.
     */
    public static class DependencyException extends RuntimeException {
        public DependencyException(String message) {
            super(message);
        }
    }
}
