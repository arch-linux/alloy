package net.alloymc.client.screen;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;
import net.alloymc.client.render.AlloyTextures;
import net.alloymc.client.ui.AlloyButton;

/**
 * Replaces Minecraft's title screen with Alloy branding.
 *
 * Layout:
 * - Full obsidian-950 background with subtle ember glow at center
 * - Alloy logo (anvil) centered above the menu
 * - "ALLOY" wordmark in large text
 * - "Forged with Alloy" tagline
 * - Menu buttons: Singleplayer, Multiplayer, Mods, Settings, Quit
 * - Version string at bottom-left
 *
 * The screen uses the same visual language as the Alloy Launcher and
 * alloymc.net — obsidian backgrounds with ember accent colors.
 */
public class AlloyTitleScreen extends AlloyScreen {

    private static final int LOGO_SIZE = 64;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 28;
    private static final int BUTTON_GAP = 6;

    private float animationProgress = 0f;

    public AlloyTitleScreen() {
        super("Alloy");
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);

        int centerX = width / 2;
        int menuTop = height / 2 + 10;

        int bx = centerX - BUTTON_WIDTH / 2;
        int by = menuTop;

        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Singleplayer", AlloyButton.Style.PRIMARY, () -> {
            // Stubbed — loader injection opens vanilla singleplayer screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP;
        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Multiplayer", AlloyButton.Style.PRIMARY, () -> {
            // Stubbed — loader injection opens vanilla multiplayer screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP;
        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Mods", AlloyButton.Style.SECONDARY, () -> {
            // Opens the Alloy mod list screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP + 8; // Extra gap before utility buttons
        int halfWidth = (BUTTON_WIDTH - BUTTON_GAP) / 2;
        addButton(new AlloyButton(bx, by, halfWidth, BUTTON_HEIGHT,
                "Settings", AlloyButton.Style.GHOST, () -> {
            // Stubbed — opens vanilla settings
        }));

        addButton(new AlloyButton(bx + halfWidth + BUTTON_GAP, by, halfWidth, BUTTON_HEIGHT,
                "Quit", AlloyButton.Style.GHOST, () -> {
            // Stubbed — exits the game
        }));

        animationProgress = 0f;
    }

    @Override
    public void tick() {
        if (animationProgress < 1f) {
            animationProgress = Math.min(1f, animationProgress + 0.05f);
        }
    }

    @Override
    protected void renderBackground() {
        // Base obsidian fill
        AlloyRenderer.fillRect(0, 0, width, height, AlloyColors.OBSIDIAN_950);

        // Subtle ember radial glow behind the logo area
        int glowSize = 300;
        int glowX = width / 2 - glowSize / 2;
        int glowY = height / 2 - glowSize / 2 - 60;
        int glowAlpha = (int) (animationProgress * 15); // Very subtle
        int glowColor = AlloyColors.withAlpha(AlloyColors.EMBER, glowAlpha / 255f);
        AlloyRenderer.fillRect(glowX, glowY, glowSize, glowSize, glowColor);
    }

    @Override
    protected void renderContent(int mouseX, int mouseY, int width, int height, float tickDelta) {
        int centerX = width / 2;

        // Logo
        int logoX = centerX - LOGO_SIZE / 2;
        int logoY = height / 2 - 100;
        AlloyRenderer.drawTexture(AlloyTextures.LOGO_RESOURCE,
                logoX, logoY, LOGO_SIZE, LOGO_SIZE);

        // "ALLOY" wordmark
        int wordmarkY = logoY + LOGO_SIZE + 12;
        AlloyRenderer.drawCenteredText("ALLOY", 0, wordmarkY, width,
                AlloyColors.TEXT_PRIMARY);

        // Tagline
        int taglineY = wordmarkY + 14;
        AlloyRenderer.drawCenteredText("Forged with Alloy", 0, taglineY, width,
                AlloyColors.TEXT_MUTED);

        // Version string (bottom-left)
        AlloyRenderer.drawTextWithShadow("Alloy 0.1.0", 4, height - 14,
                AlloyColors.TEXT_DISABLED);

        // Minecraft version (bottom-right)
        String mcVersion = "Minecraft 1.21.11";
        int mcWidth = AlloyRenderer.getTextWidth(mcVersion);
        AlloyRenderer.drawTextWithShadow(mcVersion, width - mcWidth - 4, height - 14,
                AlloyColors.TEXT_DISABLED);
    }
}
