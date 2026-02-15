package net.alloymc.loader.mod;

import net.alloymc.loader.api.ModInitializer;

import java.nio.file.Path;

/**
 * A fully loaded and initialized mod. Holds a reference to the mod's initializer
 * so lifecycle methods can be called later (e.g., shutdown hooks in future phases).
 *
 * @param metadata    the mod's descriptor
 * @param jarPath     path to the mod's JAR file
 * @param initializer the mod's entry point instance (may be null if no entrypoint declared)
 */
public record LoadedMod(ModMetadata metadata, Path jarPath, ModInitializer initializer) {

    public String id() {
        return metadata.id();
    }

    public String name() {
        return metadata.name();
    }
}
