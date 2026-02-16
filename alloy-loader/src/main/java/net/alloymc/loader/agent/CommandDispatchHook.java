package net.alloymc.loader.agent;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.permission.PermissionRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bridge between Minecraft's chat/command system and Alloy's {@link net.alloymc.api.command.CommandRegistry}.
 * Called via bytecode injection in ServerGamePacketListenerImpl.performUnsignedChatCommand.
 */
public final class CommandDispatchHook {

    private CommandDispatchHook() {}

    /**
     * Attempts to dispatch a command through Alloy's command system.
     *
     * @param handler the ServerGamePacketListenerImpl instance (obfuscated: ayi)
     * @param command the raw command string (without leading '/')
     * @return true if the command was handled by Alloy, false to let vanilla process it
     */
    public static boolean tryDispatch(Object handler, String command) {
        try {
            // Parse command name and args
            String[] parts = command.split("\\s+", 2);
            String cmdName = parts[0].toLowerCase();
            String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

            // Look up in Alloy's registry
            Command cmd = AlloyAPI.commandRegistry().get(cmdName);
            if (cmd == null) return false;

            // Extract player from handler via reflection (field "g" = ServerPlayer)
            Object player = getField(handler, "g");
            if (player == null) return false;

            // Wrap as ReflectivePlayer which implements both Player and CommandSender
            ReflectivePlayer reflectivePlayer = ReflectivePlayer.wrap(player);
            if (reflectivePlayer == null) return false;

            CommandSender sender = reflectivePlayer;

            // Check permission
            if (cmd.permission() != null && !cmd.permission().isEmpty()) {
                if (!sender.hasPermission(cmd.permission())) {
                    sender.sendMessage("\u00a7cYou do not have permission to use this command.");
                    return true;
                }
            }

            // Execute
            cmd.execute(sender, cmdName, args);
            return true;
        } catch (IllegalStateException e) {
            // AlloyAPI not initialized yet
            return false;
        } catch (Exception e) {
            System.err.println("[Alloy] Error dispatching command '" + command + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static Object getField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            // Try superclasses
            Class<?> clazz = obj.getClass().getSuperclass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (Exception ignored) {}
                clazz = clazz.getSuperclass();
            }
            return null;
        }
    }

    private static Object invoke(Object obj, String methodName, Object... args) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    m.setAccessible(true);
                    return m.invoke(obj, args);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * A CommandSender backed by reflection on a Minecraft ServerPlayer.
     */
    static class ReflectivePlayerSender implements CommandSender {
        private final Object player;
        private final String name;
        private final UUID uuid;
        private final PermissionRegistry permissions;

        ReflectivePlayerSender(Object player, String name, UUID uuid, PermissionRegistry permissions) {
            this.player = player;
            this.name = name;
            this.uuid = uuid;
            this.permissions = permissions;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void sendMessage(String message) {
            try {
                // Create Component.literal(message) via Component interface (yh)
                // yh.a(String) -> Component.literal(String)
                Class<?> componentClass = player.getClass().getClassLoader().loadClass("yh");
                Method literalMethod = null;
                for (Method m : componentClass.getMethods()) {
                    if (m.getName().equals("a")
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && m.getReturnType().getName().equals("yh")) {
                        literalMethod = m;
                        break;
                    }
                }
                if (literalMethod == null) {
                    // Fallback: try all methods returning the component type
                    for (Method m : componentClass.getMethods()) {
                        if (m.getName().equals("a")
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == String.class) {
                            literalMethod = m;
                            break;
                        }
                    }
                }
                if (literalMethod != null) {
                    Object component = literalMethod.invoke(null, message);

                    // Call player.sendSystemMessage(component)
                    // sendSystemMessage -> a(Component) on Entity
                    for (Method m : player.getClass().getMethods()) {
                        if (m.getName().equals("a")
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0].getName().equals("yh")) {
                            m.invoke(player, component);
                            return;
                        }
                    }

                    // Fallback: search for sendSystemMessage by parameter type being the component interface
                    for (Method m : player.getClass().getMethods()) {
                        if (m.getName().equals("a")
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0].isInterface()
                                && m.getParameterTypes()[0].isInstance(component)) {
                            m.invoke(player, component);
                            return;
                        }
                    }
                }
                System.err.println("[Alloy] Could not send message to player " + name);
            } catch (Exception e) {
                System.err.println("[Alloy] Failed to send message to " + name + ": " + e.getMessage());
            }
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public boolean hasPermission(String permission) {
            if (permissions != null && permissions.provider() != null) {
                return permissions.provider().hasPermission(uuid, name, permission);
            }
            return false;
        }

        @Override
        public boolean isOp() {
            if (permissions != null && permissions.provider() != null) {
                return permissions.provider().isOp(uuid);
            }
            return false;
        }
    }
}
