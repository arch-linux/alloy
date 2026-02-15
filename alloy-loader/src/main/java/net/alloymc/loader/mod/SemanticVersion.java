package net.alloymc.loader.mod;

/**
 * A parsed semantic version: major.minor.patch.
 *
 * <p>Supports parsing from strings like "1.21.4", "0.1.0", "2.0".
 * Missing components default to 0 — "1.2" becomes 1.2.0.
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    /**
     * Parses a version string into its components.
     *
     * @throws IllegalArgumentException if the string is not a valid version
     */
    public static SemanticVersion parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string is null or blank");
        }

        // Strip leading 'v' if present (e.g., "v1.2.3")
        String cleaned = version.strip();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }

        String[] parts = cleaned.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new SemanticVersion(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid version: '" + version + "'. Expected format: major.minor.patch (e.g., 1.21.4)");
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int c = Integer.compare(major, other.major);
        if (c != 0) return c;
        c = Integer.compare(minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
