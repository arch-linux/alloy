package net.alloymc.client.screen;

import net.alloymc.client.render.AlloyColors;
import net.alloymc.client.render.AlloyRenderer;
import net.alloymc.client.ui.AlloyButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays all installed Alloy mods.
 *
 * Layout:
 * - Title: "Installed Mods"
 * - Scrollable list of mod entries (id, name, version, description)
 * - "Back" button at the bottom
 *
 * The mod list is populated at init time from the loader's mod registry.
 * Each entry shows the mod's icon (if available), name, version, and
 * a short description.
 */
public class AlloyModListScreen extends AlloyScreen {

    private final AlloyScreen parent;
    private final List<ModEntry> mods = new ArrayList<>();
    private int scrollOffset = 0;

    /** Represents a single mod in the list. */
    public record ModEntry(String id, String name, String version, String description, List<String> authors) {}

    public AlloyModListScreen(AlloyScreen parent) {
        super("Installed Mods");
        this.parent = parent;
    }

    @Override
    public void init(int width, int height) {
        super.init(width, height);

        // Populate mod list from the loader
        // Stubbed — at runtime, the loader provides discovered mod metadata
        loadModList();

        // Back button
        int bw = 120;
        addButton(new AlloyButton(width / 2 - bw / 2, height - 40, bw, 28,
                "Back", AlloyButton.Style.SECONDARY, () -> {
            // Return to parent screen
            onClose();
        }));
    }

    /**
     * Loads mod entries from the loader's mod registry.
     * Stubbed — the loader injection populates this at runtime.
     */
    private void loadModList() {
        mods.clear();
        // The loader will inject actual mod metadata here.
        // For now, always include ourselves:
        mods.add(new ModEntry("alloy-client", "Alloy Client", "0.1.0",
                "Custom client UI for the Alloy modding ecosystem.", List.of("arch-linux")));
    }

    @Override
    protected void renderContent(int mouseX, int mouseY, int width, int height, float tickDelta) {
        // Title
        AlloyRenderer.drawCenteredText("Installed Mods", 0, 16, width,
                AlloyColors.TEXT_PRIMARY);

        // Mod count
        AlloyRenderer.drawCenteredText(mods.size() + " mod(s) loaded", 0, 30, width,
                AlloyColors.TEXT_MUTED);

        // Mod list area
        int listX = width / 2 - 160;
        int listY = 50;
        int listWidth = 320;
        int entryHeight = 48;

        AlloyRenderer.enableScissor(listX, listY, listWidth, height - 100);

        for (int i = 0; i < mods.size(); i++) {
            int ey = listY + i * (entryHeight + 4) - scrollOffset;
            if (ey + entryHeight < listY || ey > height - 50) continue;

            ModEntry mod = mods.get(i);
            renderModEntry(mod, listX, ey, listWidth, entryHeight, mouseX, mouseY);
        }

        AlloyRenderer.disableScissor();
    }

    private void renderModEntry(ModEntry mod, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        // Background
        int bg = hovered ? AlloyColors.OBSIDIAN_700 : AlloyColors.OBSIDIAN_800;
        AlloyRenderer.fillRoundedRect(x, y, w, h, 6, bg);

        // Border
        AlloyRenderer.drawRect(x, y, w, h, AlloyColors.OBSIDIAN_600);

        // Mod name + version
        AlloyRenderer.drawTextWithShadow(mod.name + " " + mod.version,
                x + 12, y + 8, AlloyColors.TEXT_PRIMARY);

        // Description
        AlloyRenderer.drawText(mod.description,
                x + 12, y + 22, AlloyColors.TEXT_SECONDARY);

        // Authors
        String authors = String.join(", ", mod.authors);
        AlloyRenderer.drawText("by " + authors,
                x + 12, y + 34, AlloyColors.TEXT_MUTED);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return false;
    }
}
