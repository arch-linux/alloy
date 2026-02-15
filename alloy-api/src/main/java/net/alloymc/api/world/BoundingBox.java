package net.alloymc.api.world;

/**
 * An axis-aligned bounding box defined by two corners.
 */
public record BoundingBox(double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ) {

    public BoundingBox {
        if (minX > maxX) { double t = minX; minX = maxX; maxX = t; }
        if (minY > maxY) { double t = minY; minY = maxY; maxY = t; }
        if (minZ > maxZ) { double t = minZ; minZ = maxZ; maxZ = t; }
    }

    public static BoundingBox of(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new BoundingBox(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location location) {
        return contains(location.x(), location.y(), location.z());
    }

    public boolean intersects(BoundingBox other) {
        return maxX >= other.minX && minX <= other.maxX
                && maxY >= other.minY && minY <= other.maxY
                && maxZ >= other.minZ && minZ <= other.maxZ;
    }

    public double volume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    public BoundingBox expand(double amount) {
        return new BoundingBox(
                minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount
        );
    }
}
