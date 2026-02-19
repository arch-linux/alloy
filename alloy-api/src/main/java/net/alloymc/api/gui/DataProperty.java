package net.alloymc.api.gui;

/**
 * A named integer property that syncs automatically between server and client
 * via Minecraft's ContainerData mechanism.
 *
 * <p>Properties are 16-bit signed values (range: -32768 to 32767). For larger values,
 * split across two properties and combine: {@code (highProperty << 16) | (lowProperty & 0xFFFF)}.
 *
 * <p>Use properties to sync progress bars, energy levels, temperatures, and other
 * numeric state without writing custom packets.
 */
public final class DataProperty {

    private final String name;
    private final int index;
    private int value;

    /**
     * Creates a new data property.
     *
     * @param name  a human-readable name (for debugging and lookup)
     * @param index the ContainerData index this property maps to
     */
    public DataProperty(String name, int index) {
        this.name = name;
        this.index = index;
        this.value = 0;
    }

    /**
     * The property name.
     */
    public String name() {
        return name;
    }

    /**
     * The ContainerData index.
     */
    public int index() {
        return index;
    }

    /**
     * The current value.
     */
    public int get() {
        return value;
    }

    /**
     * Sets the property value. Clamped to 16-bit signed range.
     *
     * @param value the new value
     */
    public void set(int value) {
        this.value = (short) value;
    }

    /**
     * Sets the value without clamping. Use when combining two properties
     * for a 32-bit value and you need the raw bits.
     *
     * @param value the raw 16-bit value
     */
    public void setRaw(int value) {
        this.value = value & 0xFFFF;
    }
}
