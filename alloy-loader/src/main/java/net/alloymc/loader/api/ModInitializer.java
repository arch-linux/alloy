package net.alloymc.loader.api;

/**
 * The entry point for an Alloy mod.
 *
 * <p>Every mod declares an entrypoint class in its {@code alloy.mod.json} that implements
 * this interface. The loader instantiates it and calls {@link #onInitialize()} during startup,
 * after all mods have been discovered and dependencies resolved.
 *
 * <p>Example:
 * <pre>{@code
 * public class MyMod implements ModInitializer {
 *     @Override
 *     public void onInitialize() {
 *         System.out.println("My mod is loaded!");
 *         // Register blocks, items, events, etc.
 *     }
 * }
 * }</pre>
 */
public interface ModInitializer {

    /**
     * Called once during game startup. Use this to register your mod's content:
     * blocks, items, entities, event handlers, commands, etc.
     *
     * <p>At this point, all mods have been discovered and their dependencies verified.
     * You can safely interact with other mods' APIs.
     */
    void onInitialize();
}
