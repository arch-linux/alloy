package net.alloymc.loader.agent;

import net.alloymc.api.gui.DataProperty;
import net.alloymc.api.gui.MenuLayout;
import net.alloymc.api.gui.SlotDefinition;
import net.alloymc.api.gui.SlotType;
import net.alloymc.api.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Concrete implementation of {@link MenuLayout.Builder}.
 */
final class MenuLayoutBuilderImpl implements MenuLayout.Builder {

    private final String title;
    private final int rows;
    private final List<SlotDefinition> slots = new ArrayList<>();
    private final List<DataProperty> properties = new ArrayList<>();

    MenuLayoutBuilderImpl(String title, int rows) {
        this.title = title;
        this.rows = rows;
    }

    @Override
    public MenuLayout.Builder slot(int index, int x, int y, SlotType type) {
        slots.add(new SlotDefinition(index, x, y, type));
        return this;
    }

    @Override
    public MenuLayout.Builder slot(int index, int x, int y, SlotType type, Predicate<ItemStack> filter) {
        slots.add(new SlotDefinition(index, x, y, type, filter));
        return this;
    }

    @Override
    public MenuLayout.Builder row(int startIndex, int y) {
        for (int col = 0; col < 9; col++) {
            slots.add(new SlotDefinition(startIndex + col, 8 + col * 18, y, SlotType.NORMAL));
        }
        return this;
    }

    @Override
    public MenuLayout.Builder property(String name, int index) {
        properties.add(new DataProperty(name, index));
        return this;
    }

    @Override
    public MenuLayout build() {
        return new MenuLayoutImpl(title, rows, slots, properties);
    }
}
