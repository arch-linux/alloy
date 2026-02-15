package net.alloymc.api.world;

import net.alloymc.api.block.BlockPosition;

import java.util.Objects;

/**
 * An immutable position in a world with precise coordinates and rotation.
 */
public record Location(World world, double x, double y, double z, float yaw, float pitch) {

    public Location(World world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    /**
     * Returns the block position at this location (floored coordinates).
     */
    public BlockPosition toBlockPosition() {
        return new BlockPosition(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z)
        );
    }

    /**
     * Returns the chunk X coordinate.
     */
    public int chunkX() {
        return (int) Math.floor(x) >> 4;
    }

    /**
     * Returns the chunk Z coordinate.
     */
    public int chunkZ() {
        return (int) Math.floor(z) >> 4;
    }

    /**
     * Distance squared to another location. Ignores world check.
     */
    public double distanceSquared(Location other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Location other) {
        return Math.sqrt(distanceSquared(other));
    }

    public Location withWorld(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public Location add(double dx, double dy, double dz) {
        return new Location(world, x + dx, y + dy, z + dz, yaw, pitch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location loc)) return false;
        return Double.compare(x, loc.x) == 0
                && Double.compare(y, loc.y) == 0
                && Double.compare(z, loc.z) == 0
                && Objects.equals(world, loc.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }
}
