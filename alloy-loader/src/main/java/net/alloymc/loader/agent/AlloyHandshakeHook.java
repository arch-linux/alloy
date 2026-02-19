package net.alloymc.loader.agent;

import net.alloymc.loader.AlloyLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge between ASM-injected bytecode and the Alloy handshake system.
 *
 * <p>Each public static method is called from ASM hooks in obfuscated Minecraft classes.
 * Methods use reflection to access MC internals since we can't import obfuscated types.
 *
 * <p>This class is self-contained within alloy-loader (no alloy-core imports) since
 * alloy-loader cannot depend on alloy-core. All handshake logic is inlined here.
 *
 * <p>Handshake flow:
 * <ol>
 *   <li>Client appends {@code \0ALLOY\0version\0protocol} to server address in handshake packet</li>
 *   <li>Server checks for marker in {@link #onHandshakeReceived}, stores result keyed by connection</li>
 *   <li>At {@link #verifyOnJoin}, non-Alloy clients are rejected if {@code requireAlloyClient=true}</li>
 * </ol>
 *
 * <p>Obfuscated mapping reference (MC 1.21.11):
 * <pre>
 *   ServerHandshakePacketListenerImpl (ayj): connection=d, handleIntention=a(Lakj;)V
 *   ClientIntentionPacket (akj):             hostName=c, hostName()=e
 *   Connection (wu):                         send=a(Packet), address=l, channel=k
 *   PlayerList (bbz):                        placeNewPlayer=a(Lwu;Laxg;Laxu;)V
 *   ServerPlayer (axg):                      getGameProfile=gI
 * </pre>
 */
public final class AlloyHandshakeHook {

    /** Marker appended to the server address in the initial handshake packet. */
    public static final String ADDRESS_MARKER = "\0ALLOY\0";

    /** Current handshake protocol version. */
    public static final int CURRENT_PROTOCOL = 1;

    /** Disconnect message for vanilla clients. */
    private static final String DISCONNECT_NOT_ALLOY =
            "This server requires Alloy. Download at alloymc.net";

    /** Disconnect message template for version mismatch. */
    private static final String DISCONNECT_VERSION_MISMATCH =
            "Alloy version mismatch. Server: %s, Client: %s";

    /** Whether to require Alloy clients. Read from system property, default true. */
    private static final boolean requireAlloyClient =
            !"false".equalsIgnoreCase(System.getProperty("alloy.requireAlloyClient", "true"));

    /**
     * Stores verified Alloy connections keyed by remote address string.
     * Populated during handshake, checked during player join.
     */
    private static final ConcurrentHashMap<String, String[]> verifiedConnections =
            new ConcurrentHashMap<>();

    private AlloyHandshakeHook() {}

    // =================== Server-Side: Handshake Phase ===================

    /**
     * Called from ASM hook at the start of ServerHandshakePacketListenerImpl.handleIntention().
     * Checks if the handshake address contains the Alloy marker.
     *
     * <p>If the marker is present, stores the parsed version/protocol keyed by connection address.
     * If no marker found and {@code requireAlloyClient=true}, the client will be rejected
     * at join time by {@link #verifyOnJoin}.
     *
     * @param handshakeHandler the ServerHandshakePacketListenerImpl instance (obfuscated: ayj)
     * @param handshakePacket  the ClientIntentionPacket instance (obfuscated: akj)
     */
    public static void onHandshakeReceived(Object handshakeHandler, Object handshakePacket) {
        try {
            // Extract the hostName from ClientIntentionPacket via accessor method e()
            String address = null;
            try {
                Method hostNameMethod = handshakePacket.getClass().getMethod("e");
                address = (String) hostNameMethod.invoke(handshakePacket);
            } catch (Exception e) {
                // Fallback: try field 'c' (hostName)
                address = (String) getField(handshakePacket, "c");
            }

            if (address == null) {
                System.err.println("[Alloy] Could not read handshake address");
                return;
            }

            // Get the Connection from the handler (field 'd')
            Object connection = getField(handshakeHandler, "d");
            String connectionKey = getConnectionKey(connection);

            // Try to parse the Alloy marker from the address
            int idx = address.indexOf(ADDRESS_MARKER);
            if (idx >= 0) {
                // Alloy client detected — parse version and protocol
                String data = address.substring(idx + ADDRESS_MARKER.length());
                String[] parts = data.split("\0");
                String clientVersion = parts.length > 0 ? parts[0] : "unknown";
                String clientProtocol = parts.length > 1 ? parts[1] : "0";

                verifiedConnections.put(connectionKey, new String[]{clientVersion, clientProtocol});
                System.out.println("[Alloy] Handshake marker detected from " + connectionKey
                        + " (v" + clientVersion + ", protocol " + clientProtocol + ")");

                // Strip the marker from the address so MC doesn't see it
                String cleanAddress = address.substring(0, idx);
                setField(handshakePacket, "c", cleanAddress);
            } else {
                // No Alloy marker — vanilla client
                if (requireAlloyClient) {
                    System.out.println("[Alloy] Rejected vanilla client from " + connectionKey
                            + " (requireAlloyClient=true)");
                    // Don't store anything — verifyOnJoin will reject
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Error in handshake hook: " + e.getMessage());
        }
    }

    // =================== Server-Side: Join Phase (Safety Net) ===================

    /**
     * Called from ASM hook at the start of PlayerList.placeNewPlayer().
     * Rejects non-Alloy clients that weren't verified during handshake.
     *
     * @param connection   the Connection object (obfuscated: wu), param 1 of placeNewPlayer
     * @param serverPlayer the ServerPlayer object (obfuscated: axg), param 2
     * @return true if the player should be rejected (caller should return immediately)
     */
    public static boolean verifyOnJoin(Object connection, Object serverPlayer) {
        try {
            if (!requireAlloyClient) return false;

            String connectionKey = getConnectionKey(connection);
            String[] payload = verifiedConnections.get(connectionKey);

            if (payload == null) {
                // Not verified — disconnect
                String playerName = getPlayerName(serverPlayer);
                System.out.println("[Alloy] Rejecting unverified client: " + playerName
                        + " from " + connectionKey);
                disconnect(connection, DISCONNECT_NOT_ALLOY);
                return true;
            }

            // Check protocol compatibility
            int clientProtocol;
            try {
                clientProtocol = Integer.parseInt(payload[1]);
            } catch (NumberFormatException e) {
                clientProtocol = 0;
            }

            if (clientProtocol != CURRENT_PROTOCOL) {
                String playerName = getPlayerName(serverPlayer);
                System.out.println("[Alloy] Rejecting incompatible protocol from " + playerName
                        + ": client=" + clientProtocol + ", server=" + CURRENT_PROTOCOL);
                disconnect(connection,
                        String.format(DISCONNECT_VERSION_MISMATCH, AlloyLoader.VERSION, payload[0]));
                verifiedConnections.remove(connectionKey);
                return true;
            }

            // Verified — log and clean up
            String playerName = getPlayerName(serverPlayer);
            System.out.println("[Alloy] Alloy client verified: " + playerName
                    + " (v" + payload[0] + ")");
            verifiedConnections.remove(connectionKey);
            return false;
        } catch (Exception e) {
            System.err.println("[Alloy] Error in join verification: " + e.getMessage());
            return false; // Don't block joins on hook failure
        }
    }

    // =================== Client-Side: Address Modification ===================

    /**
     * Called from ASM hook on the client side when creating a ClientIntentionPacket.
     * Appends the Alloy marker to the server address.
     *
     * @param originalAddress the original server address
     * @return the address with the Alloy marker appended
     */
    public static String addMarkerToAddress(String originalAddress) {
        return originalAddress + ADDRESS_MARKER + AlloyLoader.VERSION + "\0" + CURRENT_PROTOCOL;
    }

    // =================== Reflection Utilities ===================

    /**
     * Gets a unique key for a Connection object based on its remote address.
     */
    private static String getConnectionKey(Object connection) {
        if (connection == null) return "unknown";
        try {
            // Connection.address field (SocketAddress) — field 'l'
            Object addr = getField(connection, "l");
            if (addr instanceof SocketAddress sa) {
                return sa.toString();
            }
            // Fallback: channel remote address
            Object channel = getField(connection, "k");
            if (channel != null) {
                Method remoteAddress = channel.getClass().getMethod("remoteAddress");
                Object remote = remoteAddress.invoke(channel);
                if (remote != null) return remote.toString();
            }
        } catch (Exception ignored) {}
        return String.valueOf(System.identityHashCode(connection));
    }

    /**
     * Disconnects a Connection with a plain text message.
     * Constructs a Component via reflection and calls the appropriate disconnect method.
     */
    private static void disconnect(Object connection, String message) {
        try {
            ClassLoader cl = connection.getClass().getClassLoader();

            // Build a Component.literal(message) — yh.a(String)
            Class<?> componentClass = cl.loadClass("yh");
            Method literalMethod = componentClass.getMethod("a", String.class);
            Object component = literalMethod.invoke(null, message);

            // Try Connection.disconnect(DisconnectionDetails)
            // DisconnectionDetails is Connection's inner class — wu$b in 1.21.11
            try {
                Class<?> detailsClass = cl.loadClass("wu$b");
                for (var ctor : detailsClass.getConstructors()) {
                    Class<?>[] paramTypes = ctor.getParameterTypes();
                    if (paramTypes.length == 1 && componentClass.isAssignableFrom(paramTypes[0])) {
                        Object details = ctor.newInstance(component);
                        for (Method m : connection.getClass().getMethods()) {
                            if (m.getParameterCount() == 1
                                    && m.getParameterTypes()[0] == detailsClass) {
                                m.invoke(connection, details);
                                return;
                            }
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // DisconnectionDetails class name might differ
            }

            // Fallback: try any method that takes a Component-like parameter
            for (Method m : connection.getClass().getMethods()) {
                if (m.getParameterCount() == 1
                        && componentClass.isAssignableFrom(m.getParameterTypes()[0])
                        && m.getReturnType() == void.class) {
                    m.invoke(connection, component);
                    return;
                }
            }

            System.err.println("[Alloy] Could not find disconnect method on Connection");
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to disconnect: " + e.getMessage());
        }
    }

    /**
     * Extracts a player's name from a ServerPlayer via GameProfile.
     */
    private static String getPlayerName(Object serverPlayer) {
        try {
            // ServerPlayer.getGameProfile() -> gI()
            Method gp = serverPlayer.getClass().getMethod("gI");
            Object profile = gp.invoke(serverPlayer);
            if (profile != null) {
                Method getName = profile.getClass().getMethod("getName");
                Object name = getName.invoke(profile);
                if (name != null) return name.toString();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static Object getField(Object obj, String fieldName) {
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

    private static void setField(Object obj, String fieldName, Object value) {
        try {
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(obj, value);
                    return;
                } catch (NoSuchFieldException ignored) {}
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}
