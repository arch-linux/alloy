package net.alloymc.api.block;

/**
 * An immutable integer block coordinate.
 */
public record BlockPosition(int x, int y, int z) {

    public BlockPosition offset(BlockFace face) {
        return new BlockPosition(x + face.offsetX(), y + face.offsetY(), z + face.offsetZ());
    }

    public BlockPosition add(int dx, int dy, int dz) {
        return new BlockPosition(x + dx, y + dy, z + dz);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /**
     * Packs chunk coordinates into a single long for use as a map key.
     */
    public long chunkKey() {
        return ((long) chunkX() << 32) | (chunkZ() & 0xFFFFFFFFL);
    }

    public double distanceSquared(BlockPosition other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
