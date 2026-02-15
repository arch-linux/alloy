package net.alloymc.client;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.screen.AlloyTitleScreen;
import net.alloymc.client.ui.AlloyHud;
import net.alloymc.loader.api.ModInitializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Alloy Client entry point.
 *
 * Replaces Minecraft's default title screen, pause menu, and HUD with
 * Alloy-branded equivalents. Also provides a UI component framework
 * that other mods can build on.
 *
 * This is a client-only mod — it has no server-side behavior.
 */
public class AlloyClient implements ModInitializer {

    private static final Logger LOGGER = Logger.getLogger("AlloyClient");
    private static AlloyClient instance;

    private final List<HudLayer> hudLayers = new ArrayList<>();

    public static AlloyClient instance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Alloy Client initializing...");

        // Register the built-in HUD layer (FPS, coordinates, etc.)
        registerHudLayer(new AlloyHud());

        LOGGER.info("Alloy Client initialized.");
        LOGGER.info("  Title screen: AlloyTitleScreen");
        LOGGER.info("  Pause screen: AlloyPauseScreen");
        LOGGER.info("  HUD layers: " + hudLayers.size());
        LOGGER.info("  Design system: obsidian/ember palette loaded");
        LOGGER.info("  Colors: " + AlloyColors.summary());
    }

    /**
     * Register a HUD layer that renders on top of the game.
     * Layers render in registration order.
     * Other mods can call this to add their own overlays.
     */
    public void registerHudLayer(HudLayer layer) {
        hudLayers.add(layer);
    }

    /**
     * Returns all registered HUD layers (unmodifiable).
     */
    public List<HudLayer> hudLayers() {
        return Collections.unmodifiableList(hudLayers);
    }

    /**
     * Interface for HUD overlays that render on top of the game world.
     * Implement this and register via {@link #registerHudLayer(HudLayer)}.
     */
    public interface HudLayer {

        /**
         * Render this HUD layer.
         *
         * @param screenWidth  current window width in GUI-scaled pixels
         * @param screenHeight current window height in GUI-scaled pixels
         * @param tickDelta    partial tick for smooth interpolation (0.0–1.0)
         */
        void render(int screenWidth, int screenHeight, float tickDelta);

        /**
         * Whether this layer is currently visible.
         * Called every frame — return false to skip rendering.
         */
        default boolean isVisible() {
            return true;
        }
    }
}
