package net.alloymc.loader.agent;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Implements {@link World} by wrapping a Minecraft ServerLevel via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, ServerLevel = axf, Level = dwo):
 * <pre>
 *   dimension() -> aq()       (returns ResourceKey)
 *   ResourceKey.location() -> a() (returns ResourceLocation)
 *   getBlockState(BlockPos) -> a_(BlockPos) (returns BlockState)
 * </pre>
 */
public final class ReflectiveWorld implements World {

    private final Object handle; // ServerLevel instance

    private ReflectiveWorld(Object handle) {
        this.handle = handle;
    }

    /**
     * Wraps a Minecraft ServerLevel (or Level) object. Returns null if handle is null.
     */
    public static ReflectiveWorld wrap(Object serverLevel) {
        if (serverLevel == null) return null;
        return new ReflectiveWorld(serverLevel);
    }

    /** Returns the underlying Minecraft Level object. */
    public Object handle() { return handle; }

    @Override
    public String name() {
        try {
            // Level.dimension() -> ResourceKey -> location() -> ResourceLocation.toString()
            Object dimension = EventFiringHook.invokeNoArgs(handle, "aq"); // dimension()
            if (dimension != null) {
                Object location = EventFiringHook.invokeNoArgs(dimension, "a"); // location()
                if (location != null) return location.toString();
            }
        } catch (Exception ignored) {}
        return "world";
    }

    @Override
    public UUID uniqueId() {
        // Minecraft worlds don't have native UUIDs; derive from name for consistency
        return UUID.nameUUIDFromBytes(name().getBytes());
    }

    @Override
    public Block blockAt(int x, int y, int z) {
        // Construct a BlockPos and use ReflectiveBlock
        try {
            // Create BlockPos via constructor: new BlockPos(int, int, int)
            // BlockPos obfuscated class = "is"
            Class<?> blockPosClass = handle.getClass().getClassLoader().loadClass("is");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(x, y, z);
            return ReflectiveBlock.wrap(handle, blockPos, x, y, z);
        } catch (Exception e) {
            return ReflectiveBlock.wrap(handle, null, x, y, z);
        }
    }

    @Override
    public Block blockAt(Location location) {
        return blockAt(
                (int) Math.floor(location.x()),
                (int) Math.floor(location.y()),
                (int) Math.floor(location.z()));
    }

    @Override
    public Collection<? extends Entity> entities() {
        try {
            // ServerLevel.getAllEntities() -> H() returns Iterable<Entity>
            Object allEntities = EventFiringHook.invokeNoArgs(handle, "H");
            if (allEntities instanceof Iterable<?> iterable) {
                List<Entity> result = new ArrayList<>();
                for (Object mcEntity : iterable) {
                    Entity wrapped = EntityWrapperFactory.wrap(mcEntity);
                    if (wrapped != null) result.add(wrapped);
                }
                return Collections.unmodifiableList(result);
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Player> players() {
        try {
            // ServerLevel.players() -> E() returns List<ServerPlayer>
            Object playerList = EventFiringHook.invokeNoArgs(handle, "E");
            if (playerList instanceof Iterable<?> iterable) {
                List<Player> result = new ArrayList<>();
                for (Object sp : iterable) {
                    ReflectivePlayer wrapped = ReflectivePlayer.wrap(sp);
                    if (wrapped != null) result.add(wrapped);
                }
                return Collections.unmodifiableList(result);
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public Environment environment() {
        String dimName = name();
        if (dimName.contains("nether")) return Environment.NETHER;
        if (dimName.contains("the_end")) return Environment.THE_END;
        return Environment.OVERWORLD;
    }

    @Override
    public int seaLevel() {
        try {
            // Level.getSeaLevel() -> V() returns int
            Object result = EventFiringHook.invokeNoArgs(handle, "V");
            if (result instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 63;
    }

    @Override
    public int minHeight() {
        try {
            // LevelHeightAccessor.getMinY() -> K_() returns int
            Object result = EventFiringHook.invokeNoArgs(handle, "K_");
            if (result instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return -64;
    }

    @Override
    public int maxHeight() {
        try {
            // LevelHeightAccessor.getMaxY() -> aw() returns int (= minY + height)
            Object result = EventFiringHook.invokeNoArgs(handle, "aw");
            if (result instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 320;
    }

    @Override
    public boolean pvpEnabled() {
        try {
            // ServerLevel.isPvpAllowed() -> X() returns boolean
            Object result = EventFiringHook.invokeNoArgs(handle, "X");
            if (result instanceof Boolean b) return b;
        } catch (Exception ignored) {}
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReflectiveWorld other)) return false;
        return handle == other.handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }
}
