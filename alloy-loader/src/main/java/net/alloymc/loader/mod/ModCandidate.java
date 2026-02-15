package net.alloymc.loader.mod;

import java.nio.file.Path;

/**
 * A mod that has been discovered and its metadata parsed, but not yet loaded.
 * This is the intermediate state between discovery and initialization.
 *
 * @param metadata the parsed mod descriptor
 * @param jarPath  path to the mod's JAR file
 */
public record ModCandidate(ModMetadata metadata, Path jarPath) {

    /** Shorthand for the mod's unique identifier. */
    public String id() {
        return metadata.id();
    }

    /** Shorthand for the mod's display name. */
    public String name() {
        return metadata.name();
    }

    @Override
    public String toString() {
        return metadata.name() + " v" + metadata.version() + " [" + metadata.id() + "]";
    }
}
