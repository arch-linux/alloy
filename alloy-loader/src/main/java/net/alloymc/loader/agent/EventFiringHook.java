package net.alloymc.loader.agent;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.event.block.*;
import net.alloymc.api.event.inventory.*;
import net.alloymc.api.event.player.*;
import net.alloymc.api.event.entity.*;

import net.alloymc.loader.impl.TickScheduler;

import net.alloymc.api.world.Location;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge between Minecraft's server-side game logic and Alloy's {@link net.alloymc.api.event.EventBus}.
 *
 * <p>Each method is called via ASM bytecode injection into obfuscated Minecraft classes.
 * Methods use reflection to extract game state from MC objects and construct Alloy events.
 *
 * <p>Obfuscated mapping reference (MC 1.21.11):
 * <pre>
 *   PlayerList (bbz):            placeNewPlayer=a(Connection,ServerPlayer,Cookie), remove=b(ServerPlayer)
 *   ServerPlayerGameMode (axh):  destroyBlock=a(BlockPos)Z, player=d, level=c
 *   ServerGamePacketListenerImpl (ayi): player=g, handleChat=a(ChatPacket)
 *   ServerPlayer (axg):          getGameProfile=gI
 *   BlockPos (is):               getX=u, getY=v, getZ=w
 *   ServerLevel (axf)
 *   Entity (cgk):                position=dI()V3, getUUID=cY()UUID
 * </pre>
 */
public final class EventFiringHook {

    // =================== Server Lifecycle ===================

    /**
     * Called from ASM hook at the end of MinecraftServer.runServer().
     * Captures the real MinecraftServer instance and upgrades AlloyAPI from
     * the bootstrap StubServer to a real ReflectiveServer.
     *
     * @param minecraftServer the MinecraftServer instance (net.minecraft.server.MinecraftServer)
     */
    private static volatile boolean serverUpgraded = false;

    public static void onServerReady(Object minecraftServer) {
        if (serverUpgraded) return; // Only upgrade once
        serverUpgraded = true;
        try {
            java.nio.file.Path gameDir = java.nio.file.Path.of(
                    System.getProperty("alloy.gameDir",
                            System.getProperty("user.dir")));
            ReflectiveServer server = new ReflectiveServer(minecraftServer, gameDir);
            AlloyAPI.setServer(server);

            // Also upgrade the scheduler to tick-synchronized
            net.alloymc.loader.impl.TickScheduler tickScheduler =
                    net.alloymc.loader.impl.TickScheduler.instance();
            AlloyAPI.setScheduler(tickScheduler);

            // Register item, inventory, and menu factories for custom GUI support
            AlloyAPI.setItemFactory(new ReflectiveItemFactory(minecraftServer));
            AlloyAPI.setInventoryFactory(new ReflectiveInventoryFactory(minecraftServer));
            AlloyAPI.setMenuFactory(new ReflectiveMenuFactory(minecraftServer));

            System.out.println("[Alloy] Server upgraded: ReflectiveServer + TickScheduler + ItemFactory + InventoryFactory + MenuFactory active");

            // Register Alloy commands with Brigadier for tab-complete + proper execution
            registerBrigadierCommands(minecraftServer);
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to upgrade server: " + e.getMessage());
        }
    }

    /**
     * Registers all Alloy commands with Minecraft's Brigadier command dispatcher.
     * Uses reflection since Brigadier is on the app classpath, not bootstrap.
     */
    private static void registerBrigadierCommands(Object minecraftServer) {
        try {
            // Load Brigadier classes through the MC server's classloader (not system CL)
            ClassLoader cl = minecraftServer.getClass().getClassLoader();
            Class<?> dispatcherClass = cl.loadClass("com.mojang.brigadier.CommandDispatcher");
            Class<?> literalBuilderClass = cl.loadClass("com.mojang.brigadier.builder.LiteralArgumentBuilder");
            Class<?> requiredBuilderClass = cl.loadClass("com.mojang.brigadier.builder.RequiredArgumentBuilder");
            Class<?> stringArgClass = cl.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
            Class<?> argumentTypeClass = cl.loadClass("com.mojang.brigadier.arguments.ArgumentType");
            Class<?> commandInterface = cl.loadClass("com.mojang.brigadier.Command");
            Class<?> argBuilderClass = cl.loadClass("com.mojang.brigadier.builder.ArgumentBuilder");

            // Find the CommandDispatcher from MinecraftServer
            Object dispatcher = null;
            for (Method m : minecraftServer.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (m.getReturnType() == void.class || m.getReturnType().isPrimitive()) continue;
                try {
                    for (Field f : m.getReturnType().getDeclaredFields()) {
                        if (f.getType() == dispatcherClass) {
                            Object commandsObj = m.invoke(minecraftServer);
                            if (commandsObj != null) {
                                f.setAccessible(true);
                                dispatcher = f.get(commandsObj);
                            }
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                if (dispatcher != null) break;
            }

            if (dispatcher == null) {
                System.err.println("[Alloy] Could not find Brigadier CommandDispatcher");
                return;
            }

            // Get Brigadier methods
            Method literalMethod = literalBuilderClass.getMethod("literal", String.class);
            Method greedyStringMethod = stringArgClass.getMethod("greedyString");
            Method argumentMethod = requiredBuilderClass.getMethod("argument", String.class, argumentTypeClass);
            Method executesMethod = argBuilderClass.getMethod("executes", commandInterface);
            Method thenMethod = argBuilderClass.getMethod("then", argBuilderClass);
            Method registerMethod = dispatcherClass.getMethod("register", literalBuilderClass);
            Method getStringMethod = stringArgClass.getMethod("getString",
                    cl.loadClass("com.mojang.brigadier.context.CommandContext"), String.class);

            // Register each Alloy command
            Collection<net.alloymc.api.command.Command> commands = AlloyAPI.commandRegistry().all();
            int count = 0;

            for (net.alloymc.api.command.Command cmd : commands) {
                String name = cmd.name();

                // Create the command executor proxy
                Object executor = createCommandProxy(cl, commandInterface, name);

                // Build: /name [args]
                Object literal = literalMethod.invoke(null, name);
                executesMethod.invoke(literal, executor);

                // Add greedy string argument for args
                Object greedyArg = greedyStringMethod.invoke(null);
                Object argNode = argumentMethod.invoke(null, "args", greedyArg);
                executesMethod.invoke(argNode, executor);
                thenMethod.invoke(literal, argNode);

                registerMethod.invoke(dispatcher, literal);
                count++;

                // Register aliases
                if (cmd.aliases() != null) {
                    for (String alias : cmd.aliases()) {
                        Object aliasLiteral = literalMethod.invoke(null, alias);
                        executesMethod.invoke(aliasLiteral, executor);
                        Object aliasArg = argumentMethod.invoke(null, "args", greedyArg);
                        executesMethod.invoke(aliasArg, executor);
                        thenMethod.invoke(aliasLiteral, aliasArg);
                        registerMethod.invoke(dispatcher, aliasLiteral);
                    }
                }
            }

            System.out.println("[Alloy] Registered " + count + " commands with Brigadier");
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to register Brigadier commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a dynamic proxy implementing com.mojang.brigadier.Command
     * that dispatches to the Alloy command system.
     */
    private static Object createCommandProxy(ClassLoader cl, Class<?> commandInterface, String cmdName) {
        return java.lang.reflect.Proxy.newProxyInstance(cl, new Class[]{commandInterface},
                (proxy, method, args) -> {
                    // Handle Object methods properly to avoid ClassCastException
                    String methodName = method.getName();
                    switch (methodName) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "AlloyCommand[" + cmdName + "]";
                    }
                    if ("run".equals(methodName) && args != null && args.length == 1) {
                        Object ctx = args[0]; // CommandContext
                        try {
                            executeAlloyCommand(ctx, cmdName, cl);
                        } catch (Exception e) {
                            System.err.println("[Alloy] Error in command '" + cmdName + "': " + e.getMessage());
                        }
                        return 1;
                    }
                    return 0;
                });
    }

    /**
     * Executes an Alloy command from a Brigadier CommandContext (via reflection).
     */
    private static void executeAlloyCommand(Object ctx, String cmdName, ClassLoader cl) {
        try {
            net.alloymc.api.command.Command cmd = AlloyAPI.commandRegistry().get(cmdName);
            if (cmd == null) return;

            // ctx.getSource() → CommandSourceStack
            Method getSource = ctx.getClass().getMethod("getSource");
            Object source = getSource.invoke(ctx);

            // Try to get args string: StringArgumentType.getString(ctx, "args")
            String[] cmdArgs = new String[0];
            try {
                Class<?> stringArgClass = cl.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
                Method getString = stringArgClass.getMethod("getString",
                        cl.loadClass("com.mojang.brigadier.context.CommandContext"), String.class);
                String rawArgs = (String) getString.invoke(null, ctx, "args");
                if (rawArgs != null && !rawArgs.isEmpty()) {
                    cmdArgs = rawArgs.split("\\s+");
                }
            } catch (Exception ignored) {
                // No args — that's fine
            }

            // Extract player from CommandSourceStack
            Object player = getPlayerFromSource(source);

            net.alloymc.api.command.CommandSender sender;
            if (player != null) {
                ReflectivePlayer reflectivePlayer = ReflectivePlayer.wrap(player);
                if (reflectivePlayer != null) {
                    sender = reflectivePlayer;
                } else {
                    return; // Can't wrap player
                }
            } else {
                sender = new net.alloymc.api.command.CommandSender() {
                    public String name() { return "CONSOLE"; }
                    public void sendMessage(String msg) { System.out.println("[Server] " + msg); }
                    public boolean isPlayer() { return false; }
                    public boolean hasPermission(String permission) { return true; }
                    public boolean isOp() { return true; }
                };
            }

            cmd.execute(sender, cmdName, cmdArgs);
        } catch (Exception e) {
            System.err.println("[Alloy] Error executing command '" + cmdName + "': " + e.getMessage());
        }
    }

    /**
     * Extracts the ServerPlayer from a CommandSourceStack via reflection.
     */
    private static Object getPlayerFromSource(Object commandSourceStack) {
        try {
            for (Method m : commandSourceStack.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().equals("axg")) {
                    m.setAccessible(true);
                    return m.invoke(commandSourceStack);
                }
            }
            for (Method m : commandSourceStack.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().equals("cgk")) {
                    m.setAccessible(true);
                    Object entity = m.invoke(commandSourceStack);
                    if (entity != null && entity.getClass().getName().equals("axg")) {
                        return entity;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Called from ASM hook when MinecraftServer.runServer() is about to exit.
     * Resets Alloy's server state so a new world/server gets properly registered.
     */
    public static void onServerStopping() {
        if (!serverUpgraded) return;
        serverUpgraded = false;
        System.out.println("[Alloy] Server stopping — resetting server state");
    }

    /**
     * Called from ASM hook at the start of MinecraftServer.tickServer().
     * Drives the tick-synchronized scheduler.
     *
     * @param minecraftServer the MinecraftServer instance
     */
    public static void onServerTick(Object minecraftServer) {
        try {
            TickScheduler.tick();
        } catch (Exception e) {
            // Don't spam — tick errors are noisy
        }
    }

    private EventFiringHook() {}

    // =================== Player Events ===================

    /**
     * Called at the end of PlayerList.placeNewPlayer() after the player is fully initialized.
     *
     * @param serverPlayer the ServerPlayer object (obfuscated: axg)
     */
    public static void firePlayerJoin(Object serverPlayer) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return;

            PlayerJoinEvent event = new PlayerJoinEvent(player, player.name() + " joined the game");
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerJoinEvent: " + e.getMessage());
        }
    }

    /**
     * Called at the start of PlayerList.remove() before the player is disconnected.
     *
     * @param serverPlayer the ServerPlayer object
     */
    public static void firePlayerQuit(Object serverPlayer) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return;

            PlayerQuitEvent event = new PlayerQuitEvent(player, player.name() + " left the game");
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerQuitEvent: " + e.getMessage());
        }
    }

    /**
     * Called at the start of ServerGamePacketListenerImpl.handleChat() / tryHandleChat().
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param message the chat message
     * @return true if the event was cancelled (should suppress vanilla handling)
     */
    public static boolean firePlayerChat(Object handler, String message) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            PlayerChatEvent event = new PlayerChatEvent(player, message);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerChatEvent: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called at the start of performUnsignedChatCommand / handleChatCommand.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param command the command string (without leading /)
     * @return true if the event was cancelled
     */
    public static boolean firePlayerCommand(Object handler, String command) {
        try {
            // Packet handlers fire on both Netty IO and Server thread; only process once.
            if (!Thread.currentThread().getName().equals("Server thread")) return false;

            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            PlayerCommandEvent event = new PlayerCommandEvent(player, "/" + command);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerCommandEvent: " + e.getMessage());
            return false;
        }
    }

    // =================== Block Events ===================

    /**
     * Called at the start of ServerPlayerGameMode.destroyBlock(BlockPos).
     *
     * @param gameMode the ServerPlayerGameMode instance (fields: d=player, c=level)
     * @param blockPos the BlockPos being destroyed
     * @return true if the event was cancelled (should prevent block breaking)
     */
    public static boolean fireBlockBreak(Object gameMode, Object blockPos) {
        try {
            Object serverPlayer = getField(gameMode, "d");
            Object serverLevel = getField(gameMode, "c");
            if (serverPlayer == null || blockPos == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Extract BlockPos coordinates
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            net.alloymc.api.block.BlockPosition pos =
                    new net.alloymc.api.block.BlockPosition(x, y, z);

            // Create a minimal block wrapper
            ReflectiveBlock block = ReflectiveBlock.wrap(serverLevel, blockPos, x, y, z);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing BlockBreakEvent: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called at the start of ServerPlayerGameMode.useItemOn() to detect block placement.
     *
     * @param gameMode the ServerPlayerGameMode instance
     * @param serverPlayer the ServerPlayer
     * @param level the Level
     * @param itemStack the ItemStack being used
     * @param hand the InteractionHand
     * @param hitResult the BlockHitResult
     * @return true if a PlayerInteractEvent was fired and cancelled
     */
    public static boolean fireUseItemOn(Object gameMode, Object serverPlayer, Object level,
                                         Object itemStack, Object hand, Object hitResult) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Extract hit position from BlockHitResult -> getBlockPos()
            // fti.b() returns BlockPos (is), NOT a() which returns another BlockHitResult
            Object blockPos = invokeNoArgs(hitResult, "b");
            if (blockPos == null) return false;

            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            ReflectiveBlock block = ReflectiveBlock.wrap(level, blockPos, x, y, z);

            // Get the item the player is holding
            net.alloymc.api.inventory.ItemStack heldItem = player.itemInMainHand();

            // Fire PlayerInteractEvent (RIGHT_CLICK_BLOCK action)
            PlayerInteractEvent event = new PlayerInteractEvent(
                    player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, block, null, heldItem);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerInteractEvent (useItemOn): " + e.getMessage());
            return false;
        }
    }

    /**
     * Called from handlePlayerAction to detect left-click block interactions.
     *
     * @param handler the ServerGamePacketListenerImpl
     * @param blockPos the BlockPos
     * @param actionOrdinal the action ordinal (0=START_DESTROY_BLOCK)
     * @return true if cancelled
     */
    public static boolean firePlayerAction(Object handler, Object blockPos, int actionOrdinal) {
        try {
            // Only fire on START_DESTROY_BLOCK (ordinal 0) for left-click
            if (actionOrdinal != 0) return false;

            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null || blockPos == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            // Get the level from the player
            Object level = invokeNoArgs(serverPlayer, "A"); // getServerLevel / serverLevel
            if (level == null) {
                level = invokeNoArgs(serverPlayer, "ao"); // fallback: level()
            }

            ReflectiveBlock block = (level != null)
                    ? ReflectiveBlock.wrap(level, blockPos, x, y, z) : null;

            net.alloymc.api.inventory.ItemStack heldItem = player.itemInMainHand();

            PlayerInteractEvent event = new PlayerInteractEvent(
                    player, PlayerInteractEvent.Action.LEFT_CLICK_BLOCK, block, null, heldItem);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerInteractEvent (action): " + e.getMessage());
            return false;
        }
    }

    // =================== Entity Events ===================

    /**
     * Called from ServerGamePacketListenerImpl.handleInteract when a player attacks an entity.
     *
     * @param handler the ServerGamePacketListenerImpl
     * @param targetEntity the attacked entity
     * @return true if cancelled
     */
    public static boolean fireEntityAttack(Object handler, Object targetEntity) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null || targetEntity == null) return false;

            ReflectivePlayer attacker = ReflectivePlayer.wrap(serverPlayer);
            if (attacker == null) return false;

            // Create a basic EntityDamageByEntityEvent
            // We don't know exact damage at this point — use 1.0 as placeholder
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                    EntityWrapperFactory.wrap(targetEntity), attacker,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing EntityDamageByEntityEvent: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called when a player right-click interacts with an entity.
     *
     * @param handler the ServerGamePacketListenerImpl
     * @param targetEntity the entity being interacted with
     * @return true if cancelled
     */
    public static boolean fireEntityInteract(Object handler, Object targetEntity) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null || targetEntity == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(
                    player, EntityWrapperFactory.wrap(targetEntity));
            AlloyAPI.eventBus().fire(event);
            if (event.isCancelled()) return true;

            // Derived: EntityInteractEvent (entity-to-block interaction perspective)
            EntityInteractEvent eiEvent = new EntityInteractEvent(
                    EntityWrapperFactory.wrap(targetEntity), null);
            AlloyAPI.eventBus().fire(eiEvent);
            return eiEvent.isCancelled();
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerInteractEntityEvent: " + e.getMessage());
            return false;
        }
    }

    // =================== Player Movement/Death Events ===================

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleMovePlayer().
     * Fires PlayerMoveEvent with the player's current and new position.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param movePacket the ServerboundMovePlayerPacket (ajb)
     * @return true if cancelled
     */
    public static boolean handleMovePacket(Object handler, Object movePacket) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            Location from = player.location();
            if (from == null) return false;

            // Extract new position from packet fields (doubles for x/y/z, floats for yaw/pitch)
            double x = from.x(), y = from.y(), z = from.z();
            float yaw = from.yaw(), pitch = from.pitch();

            // Collect double and float fields from the packet's superclass (ServerboundMovePlayerPacket)
            java.util.List<Double> doubles = new java.util.ArrayList<>();
            java.util.List<Float> floats = new java.util.ArrayList<>();
            Class<?> packetClass = movePacket.getClass().getSuperclass();
            if (packetClass == null || packetClass == Object.class) {
                packetClass = movePacket.getClass();
            }
            for (java.lang.reflect.Field f : packetClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == double.class) {
                    doubles.add(f.getDouble(movePacket));
                } else if (f.getType() == float.class) {
                    floats.add(f.getFloat(movePacket));
                }
            }
            if (doubles.size() >= 3) {
                x = doubles.get(0); y = doubles.get(1); z = doubles.get(2);
            }
            if (floats.size() >= 2) {
                yaw = floats.get(0); pitch = floats.get(1);
            }

            Location to = new Location(from.world(), x, y, z, yaw, pitch);

            // Skip if position hasn't changed meaningfully
            if (Math.abs(from.x() - to.x()) < 0.001
                    && Math.abs(from.y() - to.y()) < 0.001
                    && Math.abs(from.z() - to.z()) < 0.001) {
                return false;
            }

            PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            // Don't spam for move events
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerPlayer.die(DamageSource).
     * Fires PlayerDeathEvent.
     *
     * @param serverPlayer the ServerPlayer instance
     * @param damageSource the DamageSource that killed the player
     */
    public static void firePlayerDeath(Object serverPlayer, Object damageSource) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return;

            String deathMessage = player.name() + " died";

            // Try to get death message from DamageSource
            try {
                // DamageSource.type() -> DamageType -> msgId (readable death cause)
                Object type = invokeNoArgs(damageSource, "a"); // type()
                if (type != null) {
                    Object msgId = invokeNoArgs(type, "a"); // msgId()
                    if (msgId instanceof String msg) {
                        deathMessage = player.name() + " died from " + msg;
                    }
                }
            } catch (Exception ignored) {}

            PlayerDeathEvent event = new PlayerDeathEvent(player,
                    java.util.Collections.emptyList(), 0, deathMessage);
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            System.err.println("[Alloy] Error firing PlayerDeathEvent: " + e.getMessage());
        }
    }

    /**
     * Called from ASM hook in ServerLevel.addFreshEntity(Entity).
     * Fires EntitySpawnEvent.
     *
     * @param serverLevel the ServerLevel instance
     * @param entity the entity being spawned
     * @return true if cancelled (entity should not spawn)
     */
    public static boolean fireEntitySpawn(Object serverLevel, Object entity) {
        try {
            net.alloymc.api.entity.Entity wrapped = EntityWrapperFactory.wrap(entity);
            if (wrapped == null) return false;

            EntitySpawnEvent event = new EntitySpawnEvent(wrapped,
                    EntitySpawnEvent.SpawnReason.NATURAL);
            AlloyAPI.eventBus().fire(event);
            if (event.isCancelled()) return true;

            // Derived: ItemSpawnEvent for item entities
            if (entity.getClass().getName().equals("czl")) { // ItemEntity
                ItemSpawnEvent itemEvent = new ItemSpawnEvent(wrapped);
                AlloyAPI.eventBus().fire(itemEvent);
                if (itemEvent.isCancelled()) return true;
            }

            // Derived: HangingPlaceEvent for hanging entities (painting, item frame)
            if (EntityWrapperFactory.isHangingEntity(entity)) {
                HangingPlaceEvent hangEvent = new HangingPlaceEvent(wrapped, null);
                AlloyAPI.eventBus().fire(hangEvent);
                if (hangEvent.isCancelled()) return true;
            }

            return false;
        } catch (Exception e) {
            // Don't spam for every entity spawn
            return false;
        }
    }

    /**
     * Called from ASM hook in LivingEntity.hurtServer(ServerLevel, DamageSource, float).
     * Fires EntityDamageEvent (or EntityDamageByEntityEvent if source is an entity).
     *
     * @param livingEntity the LivingEntity being damaged
     * @param serverLevel the ServerLevel
     * @param damageSource the DamageSource
     * @param amount the damage amount
     * @return true if cancelled
     */
    public static boolean fireEntityDamage(Object livingEntity, Object serverLevel,
                                            Object damageSource, float amount) {
        try {
            net.alloymc.api.entity.Entity victim = EntityWrapperFactory.wrap(livingEntity);
            if (victim == null) return false;

            // Try to determine the damage cause and attacker from DamageSource
            EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;
            net.alloymc.api.entity.Entity attacker = null;

            try {
                // DamageSource.getEntity() — the entity that caused the damage
                Object sourceEntity = invokeNoArgs(damageSource, "c"); // getEntity()
                if (sourceEntity != null) {
                    attacker = EntityWrapperFactory.wrap(sourceEntity);
                    cause = EntityDamageEvent.DamageCause.ENTITY_ATTACK;
                }

                // Try to identify specific damage types
                Object type = invokeNoArgs(damageSource, "a"); // type()
                if (type != null) {
                    String msgId = String.valueOf(invokeNoArgs(type, "a")); // msgId
                    if (msgId.contains("fall")) cause = EntityDamageEvent.DamageCause.FALL;
                    else if (msgId.contains("drown")) cause = EntityDamageEvent.DamageCause.DROWNING;
                    else if (msgId.contains("fire") || msgId.contains("inFire")
                            || msgId.contains("onFire")) cause = EntityDamageEvent.DamageCause.FIRE;
                    else if (msgId.contains("lava")) cause = EntityDamageEvent.DamageCause.LAVA;
                    else if (msgId.contains("outOfWorld")) cause = EntityDamageEvent.DamageCause.VOID;
                    else if (msgId.contains("explosion")) cause = EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
                    else if (msgId.contains("arrow") || msgId.contains("trident"))
                        cause = EntityDamageEvent.DamageCause.PROJECTILE;
                    else if (msgId.contains("cactus") || msgId.contains("sweetBerry"))
                        cause = EntityDamageEvent.DamageCause.CONTACT;
                    else if (msgId.contains("starve")) cause = EntityDamageEvent.DamageCause.CUSTOM;
                    else if (msgId.contains("magic") || msgId.contains("wither"))
                        cause = EntityDamageEvent.DamageCause.MAGIC;
                }
            } catch (Exception ignored) {}

            EntityDamageEvent event;
            if (attacker != null) {
                event = new EntityDamageByEntityEvent(victim, attacker, cause, amount);
            } else {
                event = new EntityDamageEvent(victim, cause, amount);
            }
            AlloyAPI.eventBus().fire(event);
            if (event.isCancelled()) return true;

            // Derived: VehicleDamageEvent for vehicles (AbstractMinecart, Boat, etc.)
            String victimClass = livingEntity.getClass().getName();
            if (EntityWrapperFactory.isVehicle(livingEntity)) {
                VehicleDamageEvent vEvent = new VehicleDamageEvent(victim, attacker, amount);
                AlloyAPI.eventBus().fire(vEvent);
                if (vEvent.isCancelled()) return true;
            }

            // Derived: HangingBreakEvent for hanging entities (paintings, item frames)
            if (EntityWrapperFactory.isHangingEntity(livingEntity)) {
                HangingBreakEvent hEvent = new HangingBreakEvent(victim,
                        HangingBreakEvent.RemoveCause.ENTITY, attacker);
                AlloyAPI.eventBus().fire(hEvent);
                if (hEvent.isCancelled()) return true;
            }

            return false;
        } catch (Exception e) {
            // Don't spam for every damage event
            return false;
        }
    }

    /**
     * Called from ASM hook in LivingEntity.die(DamageSource).
     * Fires EntityDeathEvent for all LivingEntities.
     *
     * @param livingEntity the LivingEntity that died
     * @param damageSource the DamageSource that killed it
     */
    public static void fireEntityDeath(Object livingEntity, Object damageSource) {
        try {
            net.alloymc.api.entity.Entity wrapped = EntityWrapperFactory.wrap(livingEntity);
            if (wrapped == null) return;

            // Skip if this is a Player — PlayerDeathEvent handles that case
            if (wrapped instanceof net.alloymc.api.entity.Player) return;

            if (wrapped instanceof net.alloymc.api.entity.LivingEntity le) {
                EntityDeathEvent event = new EntityDeathEvent(le,
                        java.util.Collections.emptyList(), 0);
                AlloyAPI.eventBus().fire(event);
            }
        } catch (Exception e) {
            // Don't spam
        }
    }

    // =================== Block Place / Sign / Bucket Events ===================

    /**
     * Called from ASM hook in BlockItem.place(BlockPlaceContext).
     * Fires BlockPlaceEvent. Returns InteractionResult.FAIL if cancelled, null otherwise.
     *
     * @param blockItem the BlockItem instance
     * @param blockPlaceContext the BlockPlaceContext
     * @return InteractionResult.FAIL if cancelled, null to continue
     */
    public static Object fireBlockPlace(Object blockItem, Object blockPlaceContext) {
        try {
            // Extract player from context: UseOnContext.getPlayer() -> o
            Object mcPlayer = invokeNoArgs(blockPlaceContext, "o");
            if (mcPlayer == null) return null;
            // Only fire for server players
            if (!mcPlayer.getClass().getName().equals("axg")) return null;

            ReflectivePlayer player = ReflectivePlayer.wrap(mcPlayer);
            if (player == null) return null;

            // Extract level from context: UseOnContext.getLevel() -> q
            Object level = invokeNoArgs(blockPlaceContext, "q");
            if (level == null) return null;

            // Extract block position from context: BlockPlaceContext.getClickedPos() -> a
            Object blockPos = invokeNoArgs(blockPlaceContext, "a");
            if (blockPos == null) return null;

            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            ReflectiveBlock replacedBlock = ReflectiveBlock.wrap(level, blockPos, x, y, z);

            // Get player's item in hand
            net.alloymc.api.inventory.ItemStack itemInHand = player.itemInMainHand();

            BlockPlaceEvent event = new BlockPlaceEvent(replacedBlock, replacedBlock, player, itemInHand);
            AlloyAPI.eventBus().fire(event);

            if (event.isCancelled()) {
                // Return InteractionResult.FAIL — InteractionResult is a sealed class, not an enum
                try {
                    Class<?> irClass = blockItem.getClass().getClassLoader().loadClass("cdc");
                    Field failField = irClass.getDeclaredField("d"); // FAIL -> d
                    failField.setAccessible(true);
                    return failField.get(null);
                } catch (Exception ignored) {}
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Called from ASM hook in ServerExplosion.interactWithBlocks(List).
     * Fires EntityExplodeEvent (and BlockExplodeEvent).
     *
     * @param explosion the ServerExplosion instance
     * @param blockPositions the list of BlockPos being destroyed
     * @return true if cancelled
     */
    public static boolean fireExplosionBlocks(Object explosion, Object blockPositions) {
        try {
            // Get the source entity from the explosion
            Object sourceEntity = invokeNoArgs(explosion, "d"); // getDirectSourceEntity
            net.alloymc.api.entity.Entity wrappedSource = null;
            if (sourceEntity != null) {
                wrappedSource = EntityWrapperFactory.wrap(sourceEntity);
            }

            // Convert BlockPos list to Block list
            java.util.List<net.alloymc.api.block.Block> blocks = new java.util.ArrayList<>();
            if (blockPositions instanceof java.util.List<?> posList) {
                // Get the level from the explosion (field or method)
                Object level = getField(explosion, "d"); // level field in ServerExplosion
                if (level == null) level = getField(explosion, "c");
                for (Object pos : posList) {
                    try {
                        int bx = getBlockPosX(pos);
                        int by = getBlockPosY(pos);
                        int bz = getBlockPosZ(pos);
                        blocks.add(ReflectiveBlock.wrap(level, pos, bx, by, bz));
                    } catch (Exception ignored) {}
                }
            }

            if (wrappedSource != null) {
                EntityExplodeEvent event = new EntityExplodeEvent(wrappedSource, blocks);
                AlloyAPI.eventBus().fire(event);
                if (event.isCancelled()) return true;

                // Derived: EntityChangeBlockEvent for each block changed by the explosion
                for (net.alloymc.api.block.Block b : blocks) {
                    EntityChangeBlockEvent ecbEvent = new EntityChangeBlockEvent(
                            wrappedSource, b, net.alloymc.api.inventory.Material.AIR);
                    AlloyAPI.eventBus().fire(ecbEvent);
                    // Individual block cancellation handled through the block list
                }
                return false;
            } else {
                // No source entity — fire BlockExplodeEvent if we have blocks
                if (!blocks.isEmpty()) {
                    BlockExplodeEvent event = new BlockExplodeEvent(blocks.get(0), blocks);
                    AlloyAPI.eventBus().fire(event);
                    return event.isCancelled();
                }
            }
        } catch (Exception e) {
            // Don't spam
        }
        return false;
    }

    /**
     * Called from ASM hook in Mob.setTarget(LivingEntity).
     * Fires EntityTargetEvent.
     *
     * @param mob the Mob setting a target
     * @param target the target LivingEntity (may be null)
     * @return true if cancelled
     */
    public static boolean fireEntityTarget(Object mob, Object target) {
        try {
            net.alloymc.api.entity.Entity wrappedMob = EntityWrapperFactory.wrap(mob);
            if (wrappedMob == null) return false;

            net.alloymc.api.entity.Entity wrappedTarget = null;
            if (target != null) {
                wrappedTarget = EntityWrapperFactory.wrap(target);
            }

            EntityTargetEvent event = new EntityTargetEvent(wrappedMob, wrappedTarget);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerPlayer.onItemPickup(ItemEntity).
     * Fires EntityPickupItemEvent.
     *
     * @param serverPlayer the player picking up
     * @param itemEntity the ItemEntity being picked up
     * @return true if cancelled
     */
    public static boolean fireEntityPickupItem(Object serverPlayer, Object itemEntity) {
        try {
            net.alloymc.api.entity.Entity player = EntityWrapperFactory.wrap(serverPlayer);
            net.alloymc.api.entity.Entity item = EntityWrapperFactory.wrap(itemEntity);
            if (player == null || item == null) return false;

            EntityPickupItemEvent event = new EntityPickupItemEvent(player, item);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerPlayer.drop(ItemStack, boolean, boolean).
     * Fires PlayerDropItemEvent.
     *
     * @param serverPlayer the ServerPlayer dropping
     * @param itemStack the MC ItemStack being dropped
     * @return true if cancelled
     */
    public static boolean firePlayerDrop(Object serverPlayer, Object itemStack) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Wrap the dropped item as an entity (we don't have the ItemEntity yet, use a placeholder)
            net.alloymc.api.entity.Entity itemDrop = new ReflectiveEntity(itemStack);

            PlayerDropItemEvent event = new PlayerDropItemEvent(player, itemDrop);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerPlayer.teleportTo(ServerLevel, ...).
     * Fires PlayerTeleportEvent (non-cancellable).
     *
     * @param serverPlayer the ServerPlayer being teleported
     * @param destLevel the destination ServerLevel
     * @param x destination x
     * @param y destination y
     * @param z destination z
     * @param yaw destination yaw
     * @param pitch destination pitch
     */
    public static void firePlayerTeleport(Object serverPlayer, Object destLevel,
                                           double x, double y, double z, float yaw, float pitch) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return;

            Location from = player.location();
            if (from == null) return;

            net.alloymc.api.world.World destWorld = ReflectiveWorld.wrap(destLevel);
            Location to = new Location(destWorld, x, y, z, yaw, pitch);

            PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to,
                    PlayerTeleportEvent.TeleportCause.PLUGIN);
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            // Don't spam
        }
    }

    /**
     * Called from ASM hook in PlayerList.respawn().
     * Fires PlayerRespawnEvent (non-cancellable) after respawn completes.
     *
     * @param resultPlayer the newly respawned ServerPlayer
     */
    public static void firePlayerRespawn(Object resultPlayer) {
        try {
            ReflectivePlayer player = ReflectivePlayer.wrap(resultPlayer);
            if (player == null) return;

            Location respawnLoc = player.location();
            PlayerRespawnEvent event = new PlayerRespawnEvent(player, respawnLoc);
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            // Don't spam
        }
    }

    /**
     * Called from ASM hook in BucketItem.use(Level, Player, InteractionHand).
     * Fires PlayerBucketEvent.
     *
     * @param level the Level
     * @param mcPlayer the MC Player
     * @param hand the InteractionHand
     * @return InteractionResult.FAIL if cancelled, null otherwise
     */
    public static Object firePlayerBucket(Object level, Object mcPlayer, Object hand) {
        try {
            if (!mcPlayer.getClass().getName().equals("axg")) return null;
            ReflectivePlayer player = ReflectivePlayer.wrap(mcPlayer);
            if (player == null) return null;

            // Determine if filling or emptying bucket based on item type
            net.alloymc.api.inventory.ItemStack mainHand = player.itemInMainHand();
            boolean filling = mainHand != null
                    && mainHand.type() == net.alloymc.api.inventory.Material.BUCKET;

            PlayerBucketEvent event = new PlayerBucketEvent(player, null,
                    filling ? net.alloymc.api.inventory.Material.BUCKET
                            : net.alloymc.api.inventory.Material.WATER_BUCKET,
                    filling);
            AlloyAPI.eventBus().fire(event);

            if (event.isCancelled()) {
                // Return InteractionResult.FAIL — InteractionResult is a sealed class, not an enum
                try {
                    Class<?> irClass = mcPlayer.getClass().getClassLoader().loadClass("cdc");
                    Field failField = irClass.getDeclaredField("d"); // FAIL -> d
                    failField.setAccessible(true);
                    return failField.get(null);
                } catch (Exception ignored) {}
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Called from ASM hook in FlowingFluid.spreadTo(LevelAccessor, BlockPos, BlockState, Direction, FluidState).
     * Fires BlockFromToEvent.
     *
     * @param levelAccessor the LevelAccessor
     * @param toBlockPos the destination BlockPos
     * @return true if cancelled
     */
    public static boolean fireLiquidSpread(Object levelAccessor, Object toBlockPos) {
        try {
            int x = getBlockPosX(toBlockPos);
            int y = getBlockPosY(toBlockPos);
            int z = getBlockPosZ(toBlockPos);

            ReflectiveBlock toBlock = ReflectiveBlock.wrap(levelAccessor, toBlockPos, x, y, z);
            BlockFromToEvent event = new BlockFromToEvent(toBlock, toBlock);
            AlloyAPI.eventBus().fire(event);
            if (event.isCancelled()) return true;

            // Derived: BlockFormEvent (liquid interactions form new blocks like cobblestone)
            BlockFormEvent formEvent = new BlockFormEvent(toBlock,
                    net.alloymc.api.inventory.Material.STONE);
            AlloyAPI.eventBus().fire(formEvent);
            return formEvent.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in FireBlock.checkBurnOut(Level, BlockPos, int, RandomSource, int).
     * Fires BlockBurnEvent.
     *
     * @param level the Level
     * @param blockPos the BlockPos that may burn
     * @return true if cancelled
     */
    public static boolean fireBlockBurn(Object level, Object blockPos) {
        try {
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);
            ReflectiveBlock block = ReflectiveBlock.wrap(level, blockPos, x, y, z);

            BlockBurnEvent event = new BlockBurnEvent(block, null);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in FireBlock.tick(BlockState, ServerLevel, BlockPos, RandomSource).
     * Fires BlockIgniteEvent for fire spread.
     *
     * @param serverLevel the ServerLevel
     * @param blockPos the BlockPos where fire is ticking
     * @return true if cancelled
     */
    public static boolean fireFireTick(Object serverLevel, Object blockPos) {
        try {
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);
            ReflectiveBlock block = ReflectiveBlock.wrap(serverLevel, blockPos, x, y, z);

            // Fire BlockIgniteEvent
            BlockIgniteEvent event = new BlockIgniteEvent(block,
                    BlockIgniteEvent.IgniteCause.SPREAD, null, null);
            AlloyAPI.eventBus().fire(event);
            if (event.isCancelled()) return true;

            // Also fire BlockSpreadEvent (fire spreading is a form of block spread)
            BlockSpreadEvent spreadEvent = new BlockSpreadEvent(block, block);
            AlloyAPI.eventBus().fire(spreadEvent);
            return spreadEvent.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in PistonBaseBlock.moveBlocks(Level, BlockPos, Direction, boolean).
     * Fires BlockPistonEvent.
     *
     * @param level the Level
     * @param blockPos the piston BlockPos
     * @param extending true if extending, false if retracting
     * @return true if cancelled
     */
    public static boolean firePistonMove(Object level, Object blockPos, boolean extending) {
        try {
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);
            ReflectiveBlock pistonBlock = ReflectiveBlock.wrap(level, blockPos, x, y, z);

            BlockPistonEvent event = new BlockPistonEvent(pistonBlock,
                    net.alloymc.api.block.BlockFace.SELF,
                    java.util.Collections.emptyList(), extending);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    // =================== ASM Bridge Methods ===================
    // These methods are called directly from ASM-injected bytecode.
    // They accept raw MC objects and delegate to the typed fire* methods above.

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleChat(ServerboundChatPacket).
     * Extracts the message string from the chat packet via reflection.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param chatPacket the ServerboundChatPacket instance (obfuscated: aik)
     * @return true if the event was cancelled
     */
    public static boolean handleChatPacket(Object handler, Object chatPacket) {
        try {
            // Packet handlers fire on both Netty IO and Server thread; only process once.
            if (!Thread.currentThread().getName().equals("Server thread")) return false;

            // Extract the message String from the chat packet.
            // ServerboundChatPacket stores the message as the first String field.
            String message = null;
            for (java.lang.reflect.Method m : chatPacket.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                    m.setAccessible(true);
                    String val = (String) m.invoke(chatPacket);
                    if (val != null && !val.isEmpty()) {
                        message = val;
                        break;
                    }
                }
            }
            // Fallback: try declared fields
            if (message == null) {
                for (Field f : chatPacket.getClass().getDeclaredFields()) {
                    if (f.getType() == String.class) {
                        f.setAccessible(true);
                        message = (String) f.get(chatPacket);
                        if (message != null && !message.isEmpty()) break;
                    }
                }
            }
            if (message == null || message.isEmpty()) return false;
            return firePlayerChat(handler, message);
        } catch (Exception e) {
            System.err.println("[Alloy] Error handling chat packet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handlePlayerAction(ServerboundPlayerActionPacket).
     * Extracts the BlockPos and action ordinal from the packet.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param actionPacket the ServerboundPlayerActionPacket instance (obfuscated: aji)
     * @return true if the event was cancelled
     */
    public static boolean handlePlayerActionPacket(Object handler, Object actionPacket) {
        try {
            // Packet handlers fire on both Netty IO and Server thread; only process once.
            if (!Thread.currentThread().getName().equals("Server thread")) return false;

            Object blockPos = null;
            int actionOrdinal = -1;

            for (Field f : actionPacket.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(actionPacket);
                if (val == null) continue;

                // BlockPos (is) — detect by checking for getX/getY/getZ methods (u/v/w)
                if (blockPos == null && hasMethod(val.getClass(), "u") && hasMethod(val.getClass(), "v")) {
                    blockPos = val;
                }
                // Action enum — detect by checking if it's an Enum type
                if (actionOrdinal < 0 && val instanceof Enum<?> e) {
                    actionOrdinal = e.ordinal();
                }
            }

            if (blockPos == null || actionOrdinal < 0) return false;
            return firePlayerAction(handler, blockPos, actionOrdinal);
        } catch (Exception e) {
            System.err.println("[Alloy] Error handling player action packet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleInteract(ServerboundInteractPacket).
     * Attempts to resolve the target entity and determine attack vs interact.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param interactPacket the ServerboundInteractPacket instance (obfuscated: aiy)
     * @return true if the event was cancelled
     */
    public static boolean handleInteractPacket(Object handler, Object interactPacket) {
        try {
            // Packet handlers fire on both Netty IO and Server thread; only process once.
            if (!Thread.currentThread().getName().equals("Server thread")) return false;

            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            // Extract entity ID from packet (first int field)
            int entityId = -1;
            for (Field f : interactPacket.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    entityId = f.getInt(interactPacket);
                    break;
                }
            }
            if (entityId < 0) return false;

            // Get the level from the player
            Object level = invokeNoArgs(serverPlayer, "A"); // getServerLevel
            if (level == null) level = invokeNoArgs(serverPlayer, "ao"); // level()
            if (level == null) return false;

            // Resolve entity from level by ID
            Object targetEntity = invokeWithInt(level, entityId);
            if (targetEntity == null) return false;

            // Determine if ATTACK or INTERACT by inspecting the action field.
            // The ATTACK action inner class has no additional fields (no hand/position).
            boolean isAttack = false;
            for (Field f : interactPacket.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(interactPacket);
                if (val != null && !val.getClass().isPrimitive() && !(val instanceof Number)
                        && val.getClass() != boolean.class && !(val instanceof Boolean)) {
                    // Check if this looks like an action object
                    String typeName = val.getClass().getName();
                    if (!typeName.equals("is") && !typeName.equals(interactPacket.getClass().getName())) {
                        // Action class — check if it has zero declared fields (ATTACK pattern)
                        Field[] actionFields = val.getClass().getDeclaredFields();
                        if (actionFields.length == 0) {
                            isAttack = true;
                        }
                        break;
                    }
                }
            }

            if (isAttack) {
                return fireEntityAttack(handler, targetEntity);
            } else {
                return fireEntityInteract(handler, targetEntity);
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Error handling interact packet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerPlayerGameMode.useItemOn().
     * Fires PlayerInteractEvent and returns the InteractionResult.FAIL object if cancelled,
     * or null if the event was not cancelled (allowing the original method to proceed).
     *
     * @return InteractionResult.FAIL if cancelled, null otherwise
     */
    public static Object checkUseItemOn(Object gameMode, Object serverPlayer, Object level,
                                         Object itemStack, Object hand, Object hitResult) {
        boolean cancelled = fireUseItemOn(gameMode, serverPlayer, level, itemStack, hand, hitResult);
        if (!cancelled) return null;

        // Return InteractionResult.FAIL via reflection (sealed class, not enum)
        try {
            Class<?> irClass = gameMode.getClass().getClassLoader().loadClass("cdc");
            Field failField = irClass.getDeclaredField("d"); // FAIL -> d
            failField.setAccessible(true);
            return failField.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    // =================== Dispenser / Tree / Potion Events ===================

    /**
     * Called from ASM hook in DispenserBlock.dispenseFrom(ServerLevel, BlockState, BlockPos).
     * Fires BlockDispenseEvent.
     *
     * @param serverLevel the ServerLevel
     * @param blockState the BlockState
     * @param blockPos the BlockPos of the dispenser
     * @return true if cancelled
     */
    public static boolean fireDispense(Object serverLevel, Object blockState, Object blockPos) {
        try {
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);
            ReflectiveBlock block = ReflectiveBlock.wrap(serverLevel, blockPos, x, y, z);

            BlockDispenseEvent event = new BlockDispenseEvent(block, null);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in TreeGrower.growTree(ServerLevel, ChunkGenerator, BlockPos, BlockState, RandomSource).
     * Fires StructureGrowEvent.
     *
     * @param serverLevel the ServerLevel
     * @param blockPos the sapling BlockPos
     * @return true if cancelled
     */
    public static boolean fireTreeGrow(Object serverLevel, Object blockPos) {
        try {
            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);
            ReflectiveBlock block = ReflectiveBlock.wrap(serverLevel, blockPos, x, y, z);

            StructureGrowEvent event = new StructureGrowEvent(block, null,
                    java.util.Collections.emptyList());
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in AbstractThrownPotion.onHit(HitResult) or
     * ThrownSplashPotion.onHitAsPotion(ServerLevel, ItemStack, HitResult).
     * Fires PotionSplashEvent.
     *
     * @param potionEntity the thrown potion entity
     */
    public static void firePotionSplash(Object potionEntity) {
        try {
            net.alloymc.api.entity.Entity wrapped = EntityWrapperFactory.wrap(potionEntity);
            if (wrapped == null) return;

            PotionSplashEvent event = new PotionSplashEvent(wrapped,
                    java.util.Collections.emptyList());
            AlloyAPI.eventBus().fire(event);
        } catch (Exception e) {
            // Don't spam
        }
    }

    // =================== Additional Packet Handlers ===================

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleSetCarriedItem(ServerboundSetCarriedItemPacket).
     * Fires PlayerItemHeldEvent.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param packet the ServerboundSetCarriedItemPacket (obfuscated: ajt)
     * @return true if cancelled
     */
    public static boolean handleSetCarriedItemPacket(Object handler, Object packet) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Extract the new slot from the packet (first int field)
            int newSlot = -1;
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    newSlot = f.getInt(packet);
                    break;
                }
            }
            if (newSlot < 0) return false;

            // Get current slot from player's inventory
            int previousSlot = 0;
            try {
                Object inventory = invokeNoArgs(serverPlayer, "gK"); // getInventory
                if (inventory != null) {
                    Object selected = getField(inventory, "m"); // selected slot field
                    if (selected instanceof Number n) previousSlot = n.intValue();
                }
            } catch (Exception ignored) {}

            PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, previousSlot, newSlot);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleSignUpdate(ServerboundSignUpdatePacket).
     * Fires SignChangeEvent.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param packet the ServerboundSignUpdatePacket (obfuscated: aka)
     * @return true if cancelled
     */
    public static boolean handleSignUpdatePacket(Object handler, Object packet) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Extract BlockPos and lines from the packet
            Object blockPos = null;
            String[] lines = null;

            for (Field f : packet.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(packet);
                if (val == null) continue;

                if (blockPos == null && hasMethod(val.getClass(), "u") && hasMethod(val.getClass(), "v")) {
                    blockPos = val;
                }
                if (lines == null && val instanceof String[] arr) {
                    lines = arr;
                }
            }

            if (blockPos == null || lines == null) return false;

            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            Object level = invokeNoArgs(serverPlayer, "A"); // serverLevel
            if (level == null) level = invokeNoArgs(serverPlayer, "ao");

            ReflectiveBlock block = level != null
                    ? ReflectiveBlock.wrap(level, blockPos, x, y, z) : null;

            SignChangeEvent event = new SignChangeEvent(block, player, lines);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Called from ASM hook in ServerGamePacketListenerImpl.handleUseItemOn(ServerboundUseItemOnPacket).
     * Fires PlayerInteractEvent (RIGHT_CLICK_BLOCK) with the player's held item.
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param useItemOnPacket the ServerboundUseItemOnPacket (ake) — has BlockHitResult and InteractionHand
     * @return true if cancelled
     */
    public static boolean handleUseItemOnPacket(Object handler, Object useItemOnPacket) {
        try {
            // This method fires on both Netty IO thread and Server thread.
            // Only process on the Server thread to avoid double-firing events.
            if (!Thread.currentThread().getName().equals("Server thread")) return false;

            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Extract BlockHitResult from packet: e() returns BlockHitResult (fti)
            Object blockHitResult = invokeNoArgs(useItemOnPacket, "e");
            if (blockHitResult == null) return false;

            // Extract BlockPos from BlockHitResult: a() returns BlockPos
            Object blockPos = invokeNoArgs(blockHitResult, "a");
            if (blockPos == null) return false;

            int x = getBlockPosX(blockPos);
            int y = getBlockPosY(blockPos);
            int z = getBlockPosZ(blockPos);

            Object level = invokeNoArgs(serverPlayer, "A"); // serverLevel
            if (level == null) level = invokeNoArgs(serverPlayer, "ao");

            ReflectiveBlock block = level != null
                    ? ReflectiveBlock.wrap(level, blockPos, x, y, z) : null;

            net.alloymc.api.inventory.ItemStack heldItem = player.itemInMainHand();

            PlayerInteractEvent event = new PlayerInteractEvent(
                    player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, block, null, heldItem);
            AlloyAPI.eventBus().fire(event);
            return event.isCancelled();
        } catch (Exception e) {
            return false;
        }
    }

    // =================== Custom Inventory Tracking ===================

    /**
     * Maps player UUID → the custom inventory they currently have open.
     * Used by container click/close handlers to determine if the event
     * involves a custom Alloy inventory vs. a vanilla one.
     */
    private static final ConcurrentHashMap<UUID, ReflectiveCustomInventory> openInventories = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ReflectiveMenuInstance> openMenus = new ConcurrentHashMap<>();

    /**
     * Registers a custom inventory as open for a player.
     * Called from ReflectivePlayer.openInventory().
     */
    public static void registerOpenInventory(UUID playerId, ReflectiveCustomInventory inventory) {
        openInventories.put(playerId, inventory);
    }

    /**
     * Unregisters a player's custom inventory (on close).
     */
    public static void unregisterOpenInventory(UUID playerId) {
        openInventories.remove(playerId);
    }

    /**
     * Gets the custom inventory a player has open, or null if none.
     */
    public static ReflectiveCustomInventory getOpenInventory(UUID playerId) {
        return openInventories.get(playerId);
    }

    /**
     * Registers a MenuInstance as open for a player.
     * Called from ReflectivePlayer.openMenu().
     */
    public static void registerMenuInstance(UUID playerId, ReflectiveMenuInstance menu) {
        openMenus.put(playerId, menu);
    }

    /**
     * Gets the MenuInstance a player has open, or null if none.
     */
    public static ReflectiveMenuInstance getOpenMenu(UUID playerId) {
        return openMenus.get(playerId);
    }

    // =================== Container Click/Close Event Handlers ===================

    /**
     * Called from ASM hook on ServerGamePacketListenerImpl.handleContainerClick.
     * Fires InventoryClickEvent for custom inventories.
     *
     * <p>Packet: ServerboundContainerClickPacket (ais)
     * <pre>
     *   slotNum()    -> f() returns short
     *   buttonNum()  -> g() returns byte
     *   clickType()  -> h() returns ClickType (dhu)
     * </pre>
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param packet  the ServerboundContainerClickPacket instance
     * @return true if the event was cancelled (skip vanilla handling)
     */
    public static boolean handleContainerClickPacket(Object handler, Object packet) {
        try {
            // Only process on the server thread. The packet arrives first on the Netty IO
            // thread where chunk data (getBlockEntity etc.) is inaccessible. MC's
            // ensureRunningOnSameThread() re-dispatches the packet to the server thread,
            // so our hook will fire again there.
            if (!"Server thread".equals(Thread.currentThread().getName())) {
                return false;
            }

            // Get player from handler field 'g'
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return false;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return false;

            // Check if player has a custom inventory open
            ReflectiveCustomInventory customInv = openInventories.get(player.uniqueId());
            if (customInv == null) return false;

            // Extract slot number from packet: ais.f() -> short
            int slotNum = -1;
            try {
                Object slotResult = invokeNoArgs(packet, "f");
                if (slotResult instanceof Number n) slotNum = n.intValue();
            } catch (Exception ignored) {}

            // Extract button number from packet: ais.g() -> byte
            int buttonNum = 0;
            try {
                Object buttonResult = invokeNoArgs(packet, "g");
                if (buttonResult instanceof Number n) buttonNum = n.intValue();
            } catch (Exception ignored) {}

            // Extract click type from packet: ais.h() -> ClickType enum (dhu)
            Object mcClickType = invokeNoArgs(packet, "h");

            // Map MC ClickType ordinal to Alloy ClickAction
            ClickAction action = mapClickAction(mcClickType, buttonNum);

            // Get clicked item from the custom inventory (if slot is valid)
            net.alloymc.api.inventory.ItemStack clickedItem = null;
            if (slotNum >= 0 && slotNum < customInv.size()) {
                clickedItem = customInv.item(slotNum);
            }

            // Check if this is a MenuLayout GUI — fire MenuClickEvent if so
            ReflectiveMenuInstance menuInstance = openMenus.get(player.uniqueId());
            if (menuInstance != null) {
                // Find the matching SlotDefinition
                net.alloymc.api.gui.SlotDefinition slotDef = null;
                for (net.alloymc.api.gui.SlotDefinition sd : menuInstance.layout().slots()) {
                    if (sd.index() == slotNum) {
                        slotDef = sd;
                        break;
                    }
                }
                MenuClickEvent menuEvent = new MenuClickEvent(
                        player, menuInstance, slotNum, slotDef, clickedItem, action);
                AlloyAPI.eventBus().fire(menuEvent);
                resyncContainer(serverPlayer);
                return true;
            }

            // Fire InventoryClickEvent for simple chest GUIs
            InventoryClickEvent event = new InventoryClickEvent(
                    player, customInv, slotNum, clickedItem, action);
            AlloyAPI.eventBus().fire(event);

            // Always cancel container clicks on custom inventories
            // (prevent item pickup/movement) — mods can override by uncancelling
            // But default behavior is: custom GUIs are display-only unless mod says otherwise
            resyncContainer(serverPlayer);
            return true; // Always suppress vanilla handling for custom inventories
        } catch (Exception e) {
            // Don't crash MC on hook failure
            return false;
        }
    }

    /**
     * Resyncs the player's open container to the client after cancelling a click.
     *
     * <p>When we suppress vanilla handling of a container click, the client has
     * already optimistically applied the click (item on cursor). We need to tell
     * the client to reset by calling broadcastFullState() on the container menu.
     *
     * <p>Mappings:
     * <pre>
     *   ServerPlayer.containerMenu -> cn (AbstractContainerMenu dhi)
     *   AbstractContainerMenu.broadcastFullState() -> e()
     * </pre>
     */
    private static void resyncContainer(Object serverPlayer) {
        try {
            Object containerMenu = getField(serverPlayer, "cn");
            if (containerMenu != null) {
                invokeNoArgs(containerMenu, "e"); // broadcastFullState()
            }
        } catch (Exception ignored) {}
    }

    /**
     * Called from ASM hook on ServerGamePacketListenerImpl.handleContainerClose.
     *
     * <p>Packet: ServerboundContainerClosePacket (ait)
     *
     * @param handler the ServerGamePacketListenerImpl instance
     * @param packet  the ServerboundContainerClosePacket instance
     */
    public static void fireContainerClose(Object handler, Object packet) {
        try {
            Object serverPlayer = getField(handler, "g");
            if (serverPlayer == null) return;

            ReflectivePlayer player = ReflectivePlayer.wrap(serverPlayer);
            if (player == null) return;

            ReflectiveCustomInventory customInv = openInventories.get(player.uniqueId());
            if (customInv == null) return;

            // Fire InventoryCloseEvent
            InventoryCloseEvent event = new InventoryCloseEvent(player, customInv);
            AlloyAPI.eventBus().fire(event);

            // Unregister both custom inventory and menu instance
            openInventories.remove(player.uniqueId());
            openMenus.remove(player.uniqueId());
        } catch (Exception ignored) {
            // Don't crash MC
        }
    }

    /**
     * Maps a MC ClickType enum value + button number to an Alloy ClickAction.
     *
     * <p>MC ClickType ordinals:
     * <pre>
     *   0 = PICKUP      (button 0 = left, button 1 = right)
     *   1 = QUICK_MOVE   (button 0 = shift+left, button 1 = shift+right)
     *   2 = SWAP         (number key)
     *   3 = CLONE        (middle click)
     *   4 = THROW        (button 0 = drop, button 1 = ctrl+drop)
     *   5 = QUICK_CRAFT  (drag)
     *   6 = PICKUP_ALL   (double click)
     * </pre>
     */
    private static ClickAction mapClickAction(Object mcClickType, int buttonNum) {
        if (mcClickType == null) return ClickAction.LEFT;
        try {
            int ordinal = ((Enum<?>) mcClickType).ordinal();
            return switch (ordinal) {
                case 0 -> buttonNum == 1 ? ClickAction.RIGHT : ClickAction.LEFT;
                case 1 -> buttonNum == 1 ? ClickAction.SHIFT_RIGHT : ClickAction.SHIFT_LEFT;
                case 2 -> ClickAction.NUMBER_KEY;
                case 3 -> ClickAction.MIDDLE;
                case 4 -> buttonNum == 1 ? ClickAction.CTRL_DROP : ClickAction.DROP;
                case 5 -> ClickAction.LEFT; // drag → map to left as closest equivalent
                case 6 -> ClickAction.DOUBLE_CLICK;
                default -> ClickAction.LEFT;
            };
        } catch (Exception e) {
            return ClickAction.LEFT;
        }
    }

    // =================== Reflection Utilities ===================

    static Object getField(Object obj, String fieldName) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (NoSuchFieldException ignored) {}
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }

    static Object invokeNoArgs(Object obj, String methodName) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m.invoke(obj);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    static int getBlockPosX(Object blockPos) {
        Object val = invokeNoArgs(blockPos, "u"); // getX -> u
        return val instanceof Number n ? n.intValue() : 0;
    }

    static int getBlockPosY(Object blockPos) {
        Object val = invokeNoArgs(blockPos, "v"); // getY -> v
        return val instanceof Number n ? n.intValue() : 0;
    }

    static int getBlockPosZ(Object blockPos) {
        Object val = invokeNoArgs(blockPos, "w"); // getZ -> w
        return val instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Checks if a class has a public no-arg method with the given name.
     */
    private static boolean hasMethod(Class<?> clazz, String methodName) {
        for (java.lang.reflect.Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Invokes a method that takes a single int parameter on the given object.
     * Used for Level.getEntity(int entityId).
     */
    private static Object invokeWithInt(Object obj, int arg) {
        try {
            for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class
                        && m.getReturnType() != void.class && m.getReturnType() != boolean.class
                        && m.getReturnType() != int.class && m.getReturnType() != long.class
                        && m.getReturnType() != double.class && m.getReturnType() != float.class) {
                    m.setAccessible(true);
                    Object result = m.invoke(obj, arg);
                    if (result != null) return result;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
