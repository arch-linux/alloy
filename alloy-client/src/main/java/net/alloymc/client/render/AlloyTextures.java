package net.alloymc.client.render;

/**
 * Resource locations for Alloy's textures.
 *
 * Textures live at {@code assets/alloy/textures/} inside the mod JAR.
 * These constants provide standardized paths that the rendering system
 * uses to load and draw textures.
 */
public final class AlloyTextures {

    private AlloyTextures() {}

    /** Namespace for all Alloy resources. */
    public static final String NAMESPACE = "alloy";

    /** The main Alloy logo (anvil icon). */
    public static final String LOGO = "textures/alloy_logo.png";

    /** Full resource path for the logo. */
    public static final String LOGO_RESOURCE = NAMESPACE + ":" + LOGO;

    /**
     * Builds a resource path under the alloy namespace.
     *
     * @param path path relative to assets/alloy/ (e.g. "textures/icon.png")
     * @return full resource location string
     */
    public static String resource(String path) {
        return NAMESPACE + ":" + path;
    }
}
