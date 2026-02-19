package net.alloymc.client.screen;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;
import net.alloymc.client.ui.AlloyButton;
import net.alloymc.client.ui.AlloyCheckbox;
import net.alloymc.client.ui.AlloyEnergyBar;
import net.alloymc.client.ui.AlloyListView;
import net.alloymc.client.ui.AlloyProgressBar;
import net.alloymc.client.ui.AlloySlider;
import net.alloymc.client.ui.AlloyTabPanel;
import net.alloymc.client.ui.AlloyTextArea;
import net.alloymc.client.ui.AlloyTextInput;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all Alloy screens.
 *
 * Extends Minecraft's screen system (via loader injection) and provides:
 * - Alloy-styled background rendering (obsidian gradient + subtle grid)
 * - A managed list of UI widgets with input routing
 * - Convenience methods for layout and rendering
 *
 * Subclasses override {@link #init(int, int)} to add widgets and
 * {@link #renderContent(int, int, int, int, float)} for custom drawing.
 */
public abstract class AlloyScreen {

    protected String title;
    protected int width;
    protected int height;
    protected final List<AlloyButton> buttons = new ArrayList<>();
    protected final List<AlloySlider> sliders = new ArrayList<>();
    protected final List<AlloyProgressBar> progressBars = new ArrayList<>();
    protected final List<AlloyEnergyBar> energyBars = new ArrayList<>();
    protected final List<AlloyTextInput> textInputs = new ArrayList<>();
    protected final List<AlloyTextArea> textAreas = new ArrayList<>();
    protected final List<AlloyCheckbox> checkboxes = new ArrayList<>();
    protected final List<AlloyTabPanel> tabPanels = new ArrayList<>();
    protected final List<AlloyListView> listViews = new ArrayList<>();

    protected AlloyScreen(String title) {
        this.title = title;
    }

    /**
     * Called when the screen is opened or resized.
     * Add buttons and set up layout here.
     *
     * @param width  screen width in GUI-scaled pixels
     * @param height screen height in GUI-scaled pixels
     */
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        buttons.clear();
        sliders.clear();
        progressBars.clear();
        energyBars.clear();
        textInputs.clear();
        textAreas.clear();
        checkboxes.clear();
        tabPanels.clear();
        listViews.clear();
    }

    /**
     * Add a button to this screen. Buttons are rendered and receive click events.
     */
    protected AlloyButton addButton(AlloyButton button) {
        buttons.add(button);
        return button;
    }

    /**
     * Render the full screen: background, content, then widgets.
     *
     * @param mouseX    mouse X position
     * @param mouseY    mouse Y position
     * @param tickDelta partial tick
     */
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        renderContent(mouseX, mouseY, width, height, tickDelta);
        for (AlloyButton button : buttons) button.render(mouseX, mouseY, tickDelta);
        for (AlloySlider slider : sliders) slider.render(mouseX, mouseY, tickDelta);
        for (AlloyProgressBar bar : progressBars) bar.render(mouseX, mouseY, tickDelta);
        for (AlloyEnergyBar bar : energyBars) bar.render(mouseX, mouseY, tickDelta);
        for (AlloyTextInput input : textInputs) input.render(mouseX, mouseY, tickDelta);
        for (AlloyTextArea area : textAreas) area.render(mouseX, mouseY, tickDelta);
        for (AlloyCheckbox cb : checkboxes) cb.render(mouseX, mouseY, tickDelta);
        for (AlloyTabPanel panel : tabPanels) panel.render(mouseX, mouseY, tickDelta);
        for (AlloyListView list : listViews) list.render(mouseX, mouseY, tickDelta);
    }

    /**
     * Renders the Alloy-styled background.
     * Obsidian-950 base with a subtle vertical gradient.
     */
    protected void renderBackground() {
        AlloyRenderer.fillGradient(0, 0, width, height,
                AlloyColors.OBSIDIAN_950, AlloyColors.OBSIDIAN_900);
    }

    /**
     * Override to render screen-specific content between the background
     * and the widget layer.
     */
    protected abstract void renderContent(int mouseX, int mouseY, int width, int height, float tickDelta);

    /**
     * Handle a mouse click. Routes to buttons first, then to
     * {@link #onMouseClicked(int, int, int)} for custom handling.
     *
     * @param mouseX mouse X
     * @param mouseY mouse Y
     * @param button mouse button (0 = left, 1 = right, 2 = middle)
     * @return true if the click was consumed
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        for (AlloyButton btn : buttons) {
            if (btn.isHovered(mouseX, mouseY) && btn.isEnabled()) {
                btn.onClick();
                return true;
            }
        }
        for (AlloySlider slider : sliders) {
            if (slider.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (AlloyTextInput input : textInputs) {
            if (input.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (AlloyCheckbox cb : checkboxes) {
            if (cb.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (AlloyTabPanel panel : tabPanels) {
            if (panel.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (AlloyListView list : listViews) {
            if (list.mouseClicked(mouseX, mouseY, button)) return true;
        }
        for (AlloyTextArea area : textAreas) {
            if (area.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return onMouseClicked(mouseX, mouseY, button);
    }

    /**
     * Override for custom mouse click handling.
     * Called after button processing.
     */
    protected boolean onMouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /**
     * Handle a key press.
     *
     * @param keyCode   GLFW key code
     * @param scanCode  scan code
     * @param modifiers modifier keys
     * @return true if the key was consumed
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (AlloyTextInput input : textInputs) {
            if (input.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /**
     * Handle a character typed. Routes to focused text inputs.
     */
    public boolean charTyped(char ch) {
        for (AlloyTextInput input : textInputs) {
            if (input.charTyped(ch)) return true;
        }
        return false;
    }

    /**
     * Handle mouse drag. Routes to sliders.
     */
    public boolean mouseDragged(int mouseX, int mouseY) {
        for (AlloySlider slider : sliders) {
            if (slider.mouseDragged(mouseX, mouseY)) return true;
        }
        return false;
    }

    /**
     * Handle mouse release. Routes to sliders.
     */
    public void mouseReleased() {
        for (AlloySlider slider : sliders) {
            slider.mouseReleased();
        }
    }

    /**
     * Handle mouse scroll. Routes to text areas and list views.
     */
    public boolean mouseScrolled(double amount) {
        for (AlloyTextArea area : textAreas) {
            if (area.mouseScrolled(amount)) return true;
        }
        for (AlloyListView list : listViews) {
            if (list.mouseScrolled(amount)) return true;
        }
        return false;
    }

    /**
     * Called every tick (20 times per second) while the screen is open.
     */
    public void tick() {
        // Override for per-tick logic
    }

    /**
     * Called when the screen is closed.
     */
    public void onClose() {
        // Override for cleanup
    }

    public String title() {
        return title;
    }
}
