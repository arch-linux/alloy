package net.alloymc.client.screen;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;
import net.alloymc.client.render.AlloyTextures;
import net.alloymc.client.ui.AlloyButton;

/**
 * Replaces Minecraft's pause/escape menu with Alloy-styled version.
 *
 * Layout:
 * - Dimmed background (game world visible behind scrim)
 * - Centered panel with obsidian-800 background
 * - Small Alloy logo at top of panel
 * - Back to Game, Settings, Disconnect/Save & Quit buttons
 */
public class AlloyPauseScreen extends AlloyScreen {

    private static final int PANEL_WIDTH = 240;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 28;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_PADDING = 20;

    private final boolean isMultiplayer;

    public AlloyPauseScreen(boolean isMultiplayer) {
        super("Game Menu");
        this.isMultiplayer = isMultiplayer;
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);

        int centerX = width / 2;
        int bx = centerX - BUTTON_WIDTH / 2;
        int panelTop = height / 2 - 80;
        int by = panelTop + PANEL_PADDING + 36; // After logo + title

        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Back to Game", AlloyButton.Style.PRIMARY, () -> {
            // Stubbed — closes the pause screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP;
        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Settings", AlloyButton.Style.SECONDARY, () -> {
            // Stubbed — opens settings screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP;
        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                "Mods", AlloyButton.Style.SECONDARY, () -> {
            // Stubbed — opens mod list screen
        }));

        by += BUTTON_HEIGHT + BUTTON_GAP + 8;
        String quitLabel = isMultiplayer ? "Disconnect" : "Save & Quit";
        addButton(new AlloyButton(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT,
                quitLabel, AlloyButton.Style.DANGER, () -> {
            // Stubbed — disconnect or save & quit
        }));
    }

    @Override
    protected void renderBackground() {
        // Scrim over the game world
        AlloyRenderer.fillRect(0, 0, width, height, AlloyColors.SCRIM);
    }

    @Override
    protected void renderContent(int mouseX, int mouseY, int width, int height, float tickDelta) {
        int centerX = width / 2;
        int panelTop = height / 2 - 80;
        int panelHeight = PANEL_PADDING * 2 + 36 + (BUTTON_HEIGHT + BUTTON_GAP) * 3 + 8 + BUTTON_HEIGHT;

        // Panel background
        AlloyRenderer.fillRoundedRect(
                centerX - PANEL_WIDTH / 2, panelTop,
                PANEL_WIDTH, panelHeight,
                8, AlloyColors.OBSIDIAN_800);

        // Panel border
        AlloyRenderer.drawRect(
                centerX - PANEL_WIDTH / 2, panelTop,
                PANEL_WIDTH, panelHeight,
                AlloyColors.OBSIDIAN_600);

        // Small logo
        int logoSize = 20;
        AlloyRenderer.drawTexture(AlloyTextures.LOGO_RESOURCE,
                centerX - logoSize / 2, panelTop + PANEL_PADDING,
                logoSize, logoSize);

        // "Game Menu" title
        AlloyRenderer.drawCenteredText("Game Menu",
                centerX - PANEL_WIDTH / 2, panelTop + PANEL_PADDING + 24,
                PANEL_WIDTH, AlloyColors.TEXT_SECONDARY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC key (256) should close the pause screen
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return false;
    }
}
