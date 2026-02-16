package net.alloymc.loader.agent;

import net.alloymc.api.Server;
import net.alloymc.api.entity.Player;
import net.alloymc.api.world.World;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Implements {@link Server} by wrapping a Minecraft MinecraftServer via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, MinecraftServer = net.minecraft.server.MinecraftServer):
 * <pre>
 *   getPlayerList()   -> aj() returns PlayerList (bbz)
 *   getAllLevels()     -> P() returns Iterable&lt;ServerLevel&gt;
 *   getTickCount()     -> am() returns int
 *   PlayerList.getPlayers() -> t() returns List&lt;ServerPlayer&gt;
 *   PlayerList.getPlayer(UUID) -> b(UUID) returns ServerPlayer (nullable)
 *   PlayerList.getPlayerByName(String) -> a(String) returns ServerPlayer (nullable)
 * </pre>
 */
public final class ReflectiveServer implements Server {

    private static final Logger LOGGER = Logger.getLogger("AlloyServer");

    private final Object handle; // MinecraftServer instance
    private final Path dataDirectory;

    public ReflectiveServer(Object minecraftServer, Path dataDirectory) {
        this.handle = minecraftServer;
        this.dataDirectory = dataDirectory;
    }

    public Object handle() { return handle; }

    @Override
    public Collection<? extends Player> onlinePlayers() {
        try {
            Object playerList = getPlayerList();
            if (playerList == null) return Collections.emptyList();

            // PlayerList.getPlayers() -> t() returns List<ServerPlayer>
            Object players = EventFiringHook.invokeNoArgs(playerList, "t");
            if (players instanceof Iterable<?> iterable) {
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
    public Optional<? extends Player> player(UUID id) {
        try {
            Object playerList = getPlayerList();
            if (playerList == null) return Optional.empty();

            // PlayerList.getPlayer(UUID) -> b(UUID)
            for (Method m : playerList.getClass().getMethods()) {
                if (m.getName().equals("b") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == UUID.class) {
                    m.setAccessible(true);
                    Object sp = m.invoke(playerList, id);
                    if (sp != null) return Optional.ofNullable(ReflectivePlayer.wrap(sp));
                    break;
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public Optional<? extends Player> player(String name) {
        try {
            Object playerList = getPlayerList();
            if (playerList == null) return Optional.empty();

            // PlayerList.getPlayerByName(String) -> a(String)
            for (Method m : playerList.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    m.setAccessible(true);
                    Object sp = m.invoke(playerList, name);
                    if (sp != null) return Optional.ofNullable(ReflectivePlayer.wrap(sp));
                    break;
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    @Override
    public Collection<? extends World> worlds() {
        try {
            // MinecraftServer.getAllLevels() -> P() returns Iterable<ServerLevel>
            Object levels = EventFiringHook.invokeNoArgs(handle, "P");
            if (levels instanceof Iterable<?> iterable) {
                List<World> result = new ArrayList<>();
                for (Object level : iterable) {
                    ReflectiveWorld w = ReflectiveWorld.wrap(level);
                    if (w != null) result.add(w);
                }
                return Collections.unmodifiableList(result);
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    @Override
    public Optional<? extends World> world(String name) {
        for (World w : worlds()) {
            if (w.name().equals(name) || w.name().endsWith(":" + name)) {
                return Optional.of(w);
            }
        }
        return Optional.empty();
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public long currentTick() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "am"); // getTickCount
            if (result instanceof Number n) return n.longValue();
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public void broadcast(String message) {
        try {
            Object playerList = getPlayerList();
            if (playerList == null) {
                LOGGER.info("[Broadcast] " + message);
                return;
            }

            // Create Component — Component.literal(String) -> b(String)
            ClassLoader cl = handle.getClass().getClassLoader();
            Class<?> componentClass = cl.loadClass("yh");
            Method literalMethod = null;
            for (Method m : componentClass.getMethods()) {
                if (m.getName().equals("b") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    literalMethod = m;
                    break;
                }
            }
            if (literalMethod == null) {
                LOGGER.info("[Broadcast] " + message);
                return;
            }
            Object component = literalMethod.invoke(null, message);

            // PlayerList.broadcastSystemMessage(Component, boolean) -> a(Component, boolean)
            for (Method m : playerList.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0].isInterface()
                        && m.getParameterTypes()[0].isInstance(component)
                        && m.getParameterTypes()[1] == boolean.class) {
                    m.setAccessible(true);
                    m.invoke(playerList, component, false);
                    return;
                }
            }

            LOGGER.info("[Broadcast] " + message);
        } catch (Exception e) {
            LOGGER.info("[Broadcast] " + message);
        }
    }

    // =================== Internals ===================

    private Object getPlayerList() {
        return EventFiringHook.invokeNoArgs(handle, "aj"); // getPlayerList
    }
}
