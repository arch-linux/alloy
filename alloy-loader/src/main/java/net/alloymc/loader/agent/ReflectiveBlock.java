package net.alloymc.loader.agent;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.World;

import java.lang.reflect.Method;

/**
 * Implements {@link Block} by wrapping a Minecraft Level + BlockPos via reflection.
 *
 * <p>Mapping reference (MC 1.21.11):
 * <pre>
 *   Level.getBlockState(BlockPos) -> a_(BlockPos) returns BlockState
 *   BlockState.getBlock() -> b() returns Block
 *   BlockState.isAir() -> g() returns boolean
 *   BlockState.liquid() -> ... (check)
 *   Block.toString() -> contains registry name like "Block{minecraft:stone}"
 * </pre>
 */
public final class ReflectiveBlock implements Block {

    private final Object level;     // Level (dwo) or ServerLevel (axf) instance
    private final Object blockPos;  // BlockPos (is) instance, may be null for synthetic blocks
    private final int x, y, z;

    private ReflectiveBlock(Object level, Object blockPos, int x, int y, int z) {
        this.level = level;
        this.blockPos = blockPos;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Wraps a block at the given position in the given level.
     */
    public static ReflectiveBlock wrap(Object level, Object blockPos, int x, int y, int z) {
        return new ReflectiveBlock(level, blockPos, x, y, z);
    }

    /** Returns the Level handle. */
    public Object levelHandle() { return level; }

    /** Returns the BlockPos handle, or null for synthetic blocks. */
    public Object blockPosHandle() { return blockPos; }

    @Override public int x() { return x; }
    @Override public int y() { return y; }
    @Override public int z() { return z; }

    @Override
    public World world() {
        return ReflectiveWorld.wrap(level);
    }

    @Override
    public Material type() {
        if (level == null || blockPos == null) return Material.AIR;
        try {
            // Level.getBlockState(BlockPos) — try a_ first, then fallback to other names
            Object blockState = invokeWithBlockPos(level, blockPos);
            if (blockState == null) return Material.AIR;

            // BlockState.getBlock() -> b()
            Object block = EventFiringHook.invokeNoArgs(blockState, "b");
            if (block == null) return Material.AIR;

            // Block.toString() returns something like "Block{minecraft:stone}"
            return materialFromBlockString(block.toString());
        } catch (Exception e) {
            return Material.AIR;
        }
    }

    @Override
    public void setType(Material material) {
        if (level == null || blockPos == null || material == null) return;
        try {
            ClassLoader cl = level.getClass().getClassLoader();

            // BuiltInRegistries.BLOCK = mi.e
            Class<?> registriesClass = cl.loadClass("mi");
            java.lang.reflect.Field blockField = registriesClass.getDeclaredField("e");
            blockField.setAccessible(true);
            Object blockRegistry = blockField.get(null);
            if (blockRegistry == null) return;

            // Identifier.parse("minecraft:name") = amo.a(String)
            Class<?> idClass = cl.loadClass("amo");
            String mcName = "minecraft:" + material.name().toLowerCase();
            Object identifier = idClass.getMethod("a", String.class).invoke(null, mcName);
            if (identifier == null) return;

            // Registry.getValue(Identifier) -> Block
            Object mcBlock = null;
            for (java.lang.reflect.Method m : blockRegistry.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(identifier)
                        && !m.getReturnType().isPrimitive()
                        && m.getReturnType() != void.class) {
                    m.setAccessible(true);
                    mcBlock = m.invoke(blockRegistry, identifier);
                    if (mcBlock != null) break;
                }
            }
            if (mcBlock == null) return;

            // Block.defaultBlockState() -> m()
            Object blockState = EventFiringHook.invokeNoArgs(mcBlock, "m");
            if (blockState == null) return;

            // Level.setBlock(BlockPos, BlockState, int flags) -> a(BlockPos, BlockState, int)
            for (java.lang.reflect.Method m : level.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 3
                        && m.getParameterTypes()[0].isInstance(blockPos)
                        && m.getParameterTypes()[1].isInstance(blockState)
                        && m.getParameterTypes()[2] == int.class
                        && m.getReturnType() == boolean.class) {
                    m.setAccessible(true);
                    m.invoke(level, blockPos, blockState, 3);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to setType at " + x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    @Override
    public Block getRelative(BlockFace face) {
        int nx = x + face.offsetX();
        int ny = y + face.offsetY();
        int nz = z + face.offsetZ();
        // Create a new BlockPos for the relative position
        try {
            if (blockPos != null) {
                Class<?> bpClass = blockPos.getClass();
                Object newPos = bpClass.getConstructor(int.class, int.class, int.class)
                        .newInstance(nx, ny, nz);
                return new ReflectiveBlock(level, newPos, nx, ny, nz);
            }
        } catch (Exception ignored) {}
        return new ReflectiveBlock(level, null, nx, ny, nz);
    }

    @Override
    public boolean isEmpty() {
        if (level == null || blockPos == null) return true;
        try {
            Object blockState = invokeWithBlockPos(level, blockPos);
            if (blockState == null) return true;
            Object result = EventFiringHook.invokeNoArgs(blockState, "l"); // isAir()
            return result instanceof Boolean b && b;
        } catch (Exception e) {
            return type() == Material.AIR;
        }
    }

    @Override
    public boolean isLiquid() {
        Material m = type();
        return m == Material.WATER || m == Material.LAVA;
    }

    @Override
    public boolean isSolid() {
        return type().isSolid();
    }

    // =================== Reflection helpers ===================

    /**
     * Calls Level.getBlockState(BlockPos) via reflection.
     * Tries method name "a_" first (common obfuscated name), then falls back to scanning.
     */
    private static Object invokeWithBlockPos(Object level, Object blockPos) {
        try {
            // Try the obfuscated method name a_(BlockPos)
            for (Method m : level.getClass().getMethods()) {
                if (m.getName().equals("a_") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(blockPos)) {
                    m.setAccessible(true);
                    return m.invoke(level, blockPos);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Extracts a Material from a block's toString() output.
     * Format is typically "Block{minecraft:stone}" or similar.
     */
    private static Material materialFromBlockString(String blockString) {
        if (blockString == null) return Material.AIR;

        // Extract the material name: "Block{minecraft:stone}" -> "STONE"
        String lower = blockString.toLowerCase();

        // Try direct name extraction from "minecraft:name" pattern
        int colonIdx = lower.indexOf(':');
        if (colonIdx >= 0) {
            int endIdx = lower.indexOf('}', colonIdx);
            if (endIdx < 0) endIdx = lower.length();
            String materialName = lower.substring(colonIdx + 1, endIdx).toUpperCase();

            try {
                return Material.valueOf(materialName);
            } catch (IllegalArgumentException ignored) {}
        }

        // Fallback: scan all Material values for a substring match
        for (Material m : Material.values()) {
            if (m == Material.AIR) continue;
            if (lower.contains(m.name().toLowerCase())) {
                return m;
            }
        }

        return Material.AIR;
    }
}
