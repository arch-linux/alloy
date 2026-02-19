package net.alloymc.loader.agent;

import net.alloymc.api.gui.DataProperty;
import net.alloymc.api.gui.MenuLayout;
import net.alloymc.api.gui.SlotDefinition;

import java.util.List;

/**
 * Concrete implementation of {@link MenuLayout}.
 * Built by {@link MenuLayoutBuilderImpl}.
 */
final class MenuLayoutImpl implements MenuLayout {

    private final String title;
    private final int rows;
    private final List<SlotDefinition> slots;
    private final List<DataProperty> properties;

    MenuLayoutImpl(String title, int rows, List<SlotDefinition> slots, List<DataProperty> properties) {
        this.title = title;
        this.rows = rows;
        this.slots = List.copyOf(slots);
        this.properties = List.copyOf(properties);
    }

    @Override
    public String title() { return title; }

    @Override
    public int rows() { return rows; }

    @Override
    public List<SlotDefinition> slots() { return slots; }

    @Override
    public List<DataProperty> properties() { return properties; }

    @Override
    public DataProperty property(String name) {
        for (DataProperty p : properties) {
            if (p.name().equals(name)) return p;
        }
        return null;
    }
}
