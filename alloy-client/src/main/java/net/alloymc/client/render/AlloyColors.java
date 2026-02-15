package net.alloymc.client.render;

/**
 * Alloy design system color constants.
 *
 * All colors match the alloymc.net design system exactly.
 * Colors are stored as ARGB integers (0xAARRGGBB) for direct use
 * with Minecraft's rendering pipeline.
 *
 * The palette is built around two families:
 * - Obsidian (dark neutrals, backgrounds and surfaces)
 * - Ember (warm orange, accents and interactive elements)
 */
public final class AlloyColors {

    private AlloyColors() {}

    // ---- Obsidian (backgrounds & surfaces) ----
    public static final int OBSIDIAN_950 = 0xFF0A0A0C;
    public static final int OBSIDIAN_900 = 0xFF121214;
    public static final int OBSIDIAN_800 = 0xFF1C1C1F;
    public static final int OBSIDIAN_700 = 0xFF2A2A2E;
    public static final int OBSIDIAN_600 = 0xFF3A3A3F;
    public static final int OBSIDIAN_500 = 0xFF52525A;

    // ---- Ember (accents & interactive) ----
    public static final int EMBER       = 0xFFFF6B00;
    public static final int EMBER_LIGHT = 0xFFFF8C33;
    public static final int EMBER_DARK  = 0xFFCC5500;
    public static final int EMBER_GLOW  = 0x40FF6B00; // 25% opacity for glow effects

    // ---- Text ----
    public static final int TEXT_PRIMARY   = 0xFFF5F5F4; // stone-100
    public static final int TEXT_SECONDARY = 0xFFA8A29E; // stone-400
    public static final int TEXT_MUTED     = 0xFF78716C; // stone-500
    public static final int TEXT_DISABLED  = 0xFF57534E; // stone-600

    // ---- Status ----
    public static final int SUCCESS = 0xFF22C55E;
    public static final int WARNING = 0xFFF59E0B;
    public static final int ERROR   = 0xFFEF4444;
    public static final int INFO    = 0xFF3B82F6;

    // ---- Utility ----
    public static final int TRANSPARENT   = 0x00000000;
    public static final int SCRIM         = 0x80000000; // 50% black overlay
    public static final int SCRIM_HEAVY   = 0xCC000000; // 80% black overlay

    /**
     * Returns a color with modified alpha.
     *
     * @param color base ARGB color
     * @param alpha alpha value 0.0 (transparent) to 1.0 (opaque)
     * @return modified color
     */
    public static int withAlpha(int color, float alpha) {
        int a = Math.clamp((int) (alpha * 255), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Linearly interpolate between two colors.
     *
     * @param from  start color (ARGB)
     * @param to    end color (ARGB)
     * @param t     interpolation factor 0.0–1.0
     * @return interpolated color
     */
    public static int lerp(int from, int to, float t) {
        t = Math.clamp(t, 0f, 1f);
        int a = (int) (((from >> 24) & 0xFF) + t * (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)));
        int r = (int) (((from >> 16) & 0xFF) + t * (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)));
        int g = (int) (((from >> 8) & 0xFF) + t * (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)));
        int b = (int) ((from & 0xFF) + t * ((to & 0xFF) - (from & 0xFF)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Returns a brief summary of the color system for logging.
     */
    public static String summary() {
        return "obsidian(6 shades) + ember(4 shades) + text(4) + status(4)";
    }
}
