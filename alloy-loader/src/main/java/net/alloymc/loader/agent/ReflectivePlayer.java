package net.alloymc.loader.agent;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.entity.LivingEntity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.inventory.Inventory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;
import net.alloymc.api.AlloyAPI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Implements {@link Player} by wrapping a Minecraft ServerPlayer via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, ServerPlayer = axg, LivingEntity = chl, Entity = cgk):
 * <pre>
 *   getGameProfile()    -> gI()    (returns GameProfile with name()/id())
 *   position()          -> dI()    (returns Vec3 with x/y/z fields g/h/i)
 *   getUUID()           -> cY()    (returns UUID)
 *   sendSystemMessage() -> a(Component)
 *   level()             -> ao()    (returns Level)
 *   getHealth()         -> eZ()    (float)
 *   getMaxHealth()      -> fq()    (float)
 *   setHealth()         -> x(float)
 *   isDeadOrDying()     -> fa()    (boolean)
 *   isShiftKeyDown()    -> cgk.cu() (boolean)
 *   getYRot()           -> cgk.ec() (float, yaw)
 *   getXRot()           -> cgk.ee() (float, pitch)
 *   getMainHandItem()   -> chl.fx() (returns ItemStack)
 *   getInventory()      -> axg.gK() (returns Inventory)
 *   gameMode field      -> axg has ServerPlayerGameMode (axh) field, call b() -> GameType ordinal
 *   experienceLevel     -> axg.cs (int field)
 * </pre>
 */
public final class ReflectivePlayer implements Player {

    private final Object handle; // ServerPlayer instance
    private final String name;
    private final UUID uuid;

    private ReflectivePlayer(Object handle, String name, UUID uuid) {
        this.handle = handle;
        this.name = name;
        this.uuid = uuid;
    }

    /**
     * Wraps a Minecraft ServerPlayer object. Returns null if extraction fails.
     */
    public static ReflectivePlayer wrap(Object serverPlayer) {
        try {
            Object gameProfile = EventFiringHook.invokeNoArgs(serverPlayer, "gI");
            if (gameProfile == null) return null;

            String name = (String) EventFiringHook.invokeNoArgs(gameProfile, "name");
            UUID uuid = (UUID) EventFiringHook.invokeNoArgs(gameProfile, "id");
            if (name == null || uuid == null) return null;

            return new ReflectivePlayer(serverPlayer, name, uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns the underlying Minecraft ServerPlayer object. */
    public Object handle() { return handle; }

    // =================== Entity ===================

    @Override public UUID uniqueId() { return uuid; }
    @Override public String name() { return name; }
    @Override public String displayName() { return name; }
    @Override public EntityType type() { return EntityType.PLAYER; }

    @Override
    public boolean isValid() {
        try {
            Object alive = EventFiringHook.invokeNoArgs(handle, "cb");
            return alive instanceof Boolean b && b;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public Location location() {
        try {
            Object pos = EventFiringHook.invokeNoArgs(handle, "dI"); // position() -> Vec3
            if (pos == null) return null;
            double x = getDoubleField(pos, "g"); // Vec3.x
            double y = getDoubleField(pos, "h"); // Vec3.y
            double z = getDoubleField(pos, "i"); // Vec3.z
            float yaw = getFloatMethod(handle, "ec");   // getYRot
            float pitch = getFloatMethod(handle, "ee");  // getXRot
            return new Location(world(), x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public World world() {
        try {
            Object level = EventFiringHook.invokeNoArgs(handle, "ao"); // level()
            if (level == null) return null;
            return ReflectiveWorld.wrap(level);
        } catch (Exception e) {
            return null;
        }
    }

    @Override public void remove() { /* no-op for players */ }

    @Override
    public void teleport(Location destination) {
        try {
            if (destination == null) return;
            World destWorld = destination.world();

            // Get destination ServerLevel
            Object destLevel = null;
            if (destWorld instanceof ReflectiveWorld rw) {
                destLevel = rw.handle();
            }
            if (destLevel == null) {
                destLevel = EventFiringHook.invokeNoArgs(handle, "ao");
            }
            if (destLevel == null) return;

            // ServerPlayer.teleportTo(ServerLevel, x, y, z, yaw, pitch)
            // Obfuscated: axg.a(axf, double, double, double, float, float)
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 6) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0].isInstance(destLevel)
                            && params[1] == double.class && params[2] == double.class
                            && params[3] == double.class
                            && params[4] == float.class && params[5] == float.class) {
                        m.setAccessible(true);
                        m.invoke(handle, destLevel, destination.x(), destination.y(),
                                destination.z(), destination.yaw(), destination.pitch());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to teleport " + name + ": " + e.getMessage());
        }
    }

    @Override
    public boolean hasMetadata(String key) {
        return MetadataStore.has(uuid, key);
    }

    @Override
    public void setMetadata(String key, Object value) {
        MetadataStore.set(uuid, key, value);
    }

    @Override
    public Object getMetadata(String key) {
        return MetadataStore.get(uuid, key);
    }

    @Override
    public void removeMetadata(String key) {
        MetadataStore.remove(uuid, key);
    }

    // =================== LivingEntity ===================

    @Override
    public double health() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "eZ"); // getHealth -> float
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 20.0;
    }

    @Override
    public double maxHealth() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "fq"); // getMaxHealth -> float
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 20.0;
    }

    @Override
    public void setHealth(double health) {
        try {
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("x") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == float.class) {
                    m.setAccessible(true);
                    m.invoke(handle, (float) health);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void damage(double amount) {
        try {
            Object level = EventFiringHook.invokeNoArgs(handle, "ao");
            if (level == null) return;
            Object damageSources = EventFiringHook.invokeNoArgs(level, "ad_");
            if (damageSources == null) return;
            Object genericSource = EventFiringHook.invokeNoArgs(damageSources, "a");
            if (genericSource == null) return;

            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == float.class
                        && m.getReturnType() == boolean.class
                        && m.getParameterTypes()[0].isInstance(genericSource)) {
                    m.setAccessible(true);
                    m.invoke(handle, genericSource, (float) amount);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void damage(double amount, Entity source) {
        damage(amount);
    }

    @Override
    public boolean isDead() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "fa");
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {}
        return false;
    }

    @Override public LivingEntity target() { return null; }
    @Override public void setTarget(LivingEntity target) { }

    // =================== CommandSender ===================

    @Override
    public boolean isPlayer() { return true; }

    // =================== Player ===================

    @Override
    public void sendMessage(String message) {
        try {
            Class<?> componentClass = handle.getClass().getClassLoader().loadClass("yh");
            Method literalMethod = null;
            for (Method m : componentClass.getMethods()) {
                if (m.getName().equals("b") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    literalMethod = m;
                    break;
                }
            }
            if (literalMethod != null) {
                Object component = literalMethod.invoke(null, message);
                // ServerPlayer.sendSystemMessage(Component) -> a(Component)
                for (Method m : handle.getClass().getMethods()) {
                    if (m.getName().equals("a") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInterface()
                            && m.getParameterTypes()[0].isInstance(component)) {
                        m.invoke(handle, component);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to send message to " + name + ": " + e.getMessage());
        }
    }

    @Override public void sendMessage(String message, MessageType type) { sendMessage(message); }

    @Override
    public ItemStack itemInMainHand() {
        try {
            Object mcItem = EventFiringHook.invokeNoArgs(handle, "fx"); // getMainHandItem
            if (mcItem != null) return ReflectiveItemStack.wrap(mcItem);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Inventory inventory() {
        try {
            Object mcInventory = EventFiringHook.invokeNoArgs(handle, "gK"); // getInventory
            if (mcInventory != null) return ReflectiveInventory.wrap(mcInventory);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isSneaking() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "cu"); // isShiftKeyDown
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public boolean isCreativeMode() {
        return getGameModeOrdinal() == 1; // CREATIVE = 1
    }

    @Override
    public boolean isSurvivalMode() {
        return getGameModeOrdinal() == 0; // SURVIVAL = 0
    }

    @Override
    public boolean isSpectatorMode() {
        return getGameModeOrdinal() == 3; // SPECTATOR = 3
    }

    @Override
    public Block targetBlock(int maxDistance) {
        // Ray-trace is complex; return null for now
        return null;
    }

    @Override
    public BlockFace targetBlockFace(int maxDistance) {
        return null;
    }

    @Override
    public BlockFace facing() {
        float yaw = getFloatMethod(handle, "ec"); // getYRot
        // Normalize yaw to 0-360
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // Cached reflection handles for sendBlockChange (resolved once, reused)
    private static volatile Object cachedBlockRegistry;
    private static volatile Method cachedIdentifierParse;
    private static volatile Class<?> cachedPacketClass;

    @Override
    public void sendBlockChange(Location location, Material material) {
        try {
            ClassLoader cl = handle.getClass().getClassLoader();
            int bx = (int) Math.floor(location.x());
            int by = (int) Math.floor(location.y());
            int bz = (int) Math.floor(location.z());

            // 1. Create BlockPos
            Class<?> blockPosClass = cl.loadClass("is");
            Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(bx, by, bz);

            // 2. Get Block registry (BuiltInRegistries = mi, BLOCK field = e)
            if (cachedBlockRegistry == null) {
                Class<?> registriesClass = cl.loadClass("mi");
                Field blockField = registriesClass.getDeclaredField("e");
                blockField.setAccessible(true);
                cachedBlockRegistry = blockField.get(null);
                System.out.println("[Alloy] sendBlockChange: registry resolved = " + cachedBlockRegistry.getClass().getName());
            }
            Object blockRegistry = cachedBlockRegistry;

            // 3. Create Identifier (formerly ResourceLocation = amo)
            //    Identifier.parse(String) -> static method a(String)
            if (cachedIdentifierParse == null) {
                Class<?> idClass = cl.loadClass("amo");
                cachedIdentifierParse = idClass.getMethod("a", String.class);
            }
            String mcName = "minecraft:" + material.name().toLowerCase();
            Object identifier = cachedIdentifierParse.invoke(null, mcName);

            // 4. Registry.getValue(Identifier) -> Block
            Object mcBlock = null;
            for (Method m : blockRegistry.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(identifier)
                        && !m.getReturnType().isPrimitive()
                        && m.getReturnType() != void.class) {
                    m.setAccessible(true);
                    mcBlock = m.invoke(blockRegistry, identifier);
                    if (mcBlock != null) break;
                }
            }
            if (mcBlock == null) { System.err.println("[Alloy] sendBlockChange: block lookup FAILED for " + mcName); return; }

            // 5. Block.defaultBlockState() -> m()
            Object blockState = EventFiringHook.invokeNoArgs(mcBlock, "m");
            if (blockState == null) { System.err.println("[Alloy] sendBlockChange: defaultBlockState FAILED"); return; }

            // 6. Create ClientboundBlockUpdatePacket (adj)
            if (cachedPacketClass == null) {
                cachedPacketClass = cl.loadClass("adj");
            }
            Object packet = null;
            for (var ctor : cachedPacketClass.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 2
                        && ctor.getParameterTypes()[0].isInstance(blockPos)
                        && ctor.getParameterTypes()[1].isInstance(blockState)) {
                    ctor.setAccessible(true);
                    packet = ctor.newInstance(blockPos, blockState);
                    break;
                }
            }
            if (packet == null) { System.err.println("[Alloy] sendBlockChange: packet ctor FAILED"); return; }

            // 7. Send via ServerPlayer.connection(g).send(b)
            Object connection = EventFiringHook.getField(handle, "g");
            if (connection == null) { System.err.println("[Alloy] sendBlockChange: connection null"); return; }

            for (Method m : connection.getClass().getMethods()) {
                if (m.getName().equals("b") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(packet)) {
                    m.invoke(connection, packet);
                    System.out.println("[Alloy] sendBlockChange OK: " + material + " at " + bx + "," + by + "," + bz);
                    return;
                }
            }
            System.err.println("[Alloy] sendBlockChange: no send method found on " + connection.getClass().getName());
        } catch (Exception e) {
            System.err.println("[Alloy] sendBlockChange EXCEPTION: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void kick(String reason) {
        try {
            // Get connection field from ServerPlayer
            // ServerPlayer has a 'connection' field (ServerGamePacketListenerImpl)
            Object connection = EventFiringHook.getField(handle, "g");
            if (connection == null) return;

            // Create Component from reason string — Component.literal(String) -> b(String)
            Class<?> componentClass = handle.getClass().getClassLoader().loadClass("yh");
            Method literalMethod = null;
            for (Method m : componentClass.getMethods()) {
                if (m.getName().equals("b") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    literalMethod = m;
                    break;
                }
            }
            if (literalMethod == null) return;

            Object component = literalMethod.invoke(null, reason != null ? reason : "Kicked");

            // Call disconnect(Component) on the connection handler
            // disconnect(Component) -> a(Component) on ServerCommonPacketListenerImpl (ayf)
            for (Method m : connection.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInterface()
                        && m.getParameterTypes()[0].isInstance(component)) {
                    m.setAccessible(true);
                    m.invoke(connection, component);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to kick " + name + ": " + e.getMessage());
        }
    }

    @Override public boolean hasPlayedBefore() {
        // Check if world/playerdata/{uuid}.dat_old exists — MC creates this backup
        // after first disconnect, so its presence means the player has played before.
        // Also check for the main .dat file as a fallback (exists from prior sessions).
        try {
            java.nio.file.Path gameDir = java.nio.file.Path.of(
                    System.getProperty("alloy.gameDir", System.getProperty("user.dir")));
            java.nio.file.Path datOld = gameDir.resolve("world/playerdata/" + uniqueId() + ".dat_old");
            return java.nio.file.Files.exists(datOld);
        } catch (Exception e) {
            return false;
        }
    }
    @Override public long firstPlayed() { return 0; }
    @Override public long lastPlayed() { return System.currentTimeMillis(); }
    @Override public boolean isOnline() { return true; }

    @Override
    public String address() {
        try {
            Object connection = EventFiringHook.getField(handle, "g");
            if (connection != null) {
                Object addr = EventFiringHook.invokeNoArgs(connection, "n");
                return addr != null ? addr.toString() : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    @Override
    public int level() {
        try {
            // experienceLevel field in ServerPlayer — try field name "cs"
            Field f = findField(handle.getClass(), "cs", int.class);
            if (f != null) {
                f.setAccessible(true);
                return f.getInt(handle);
            }
            // Fallback: scan for int fields named with typical pattern
            for (Field field : handle.getClass().getFields()) {
                if (field.getType() == int.class && field.getName().equals("cs")) {
                    field.setAccessible(true);
                    return field.getInt(handle);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // =================== PermissionHolder ===================

    @Override
    public boolean hasPermission(String permission) {
        return AlloyAPI.permissionRegistry().provider().hasPermission(uuid, name, permission);
    }

    @Override
    public boolean isOp() {
        return AlloyAPI.permissionRegistry().provider().isOp(uuid);
    }

    // =================== Internal helpers ===================

    /**
     * Gets the game mode ordinal: SURVIVAL=0, CREATIVE=1, ADVENTURE=2, SPECTATOR=3
     */
    private int getGameModeOrdinal() {
        try {
            // ServerPlayer has a ServerPlayerGameMode field (axh)
            // ServerPlayerGameMode has a GameType field, accessible via b() -> GameType
            // GameType is an enum with ordinals 0-3
            Object gameMode = findGameModeField();
            if (gameMode == null) return 0;

            // Call b() on ServerPlayerGameMode to get GameType
            Object gameType = EventFiringHook.invokeNoArgs(gameMode, "b");
            if (gameType instanceof Enum<?> e) return e.ordinal();
        } catch (Exception ignored) {}
        return 0; // default SURVIVAL
    }

    private Object findGameModeField() {
        try {
            // Look for a field of type axh (ServerPlayerGameMode) in the player
            for (Field f : handle.getClass().getDeclaredFields()) {
                if (f.getType().getName().equals("axh")) {
                    f.setAccessible(true);
                    return f.get(handle);
                }
            }
            // Also check superclasses
            Class<?> clazz = handle.getClass().getSuperclass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType().getName().equals("axh")) {
                        f.setAccessible(true);
                        return f.get(handle);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Field findField(Class<?> clazz, String name, Class<?> type) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                if (f.getType() == type) return f;
            } catch (NoSuchFieldException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }

    // =================== Reflection helpers ===================

    private static double getDoubleField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getDouble(obj);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static float getFloatMethod(Object obj, String methodName) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0
                        && (m.getReturnType() == float.class || m.getReturnType() == Float.class)) {
                    m.setAccessible(true);
                    return (float) m.invoke(obj);
                }
            }
        } catch (Exception ignored) {}
        return 0f;
    }
}
