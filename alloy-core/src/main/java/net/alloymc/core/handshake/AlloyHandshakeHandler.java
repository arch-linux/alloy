package net.alloymc.core.handshake;

import com.google.gson.Gson;

import net.alloymc.loader.AlloyLoader;

import java.util.UUID;

/**
 * Handles incoming Alloy handshake payloads from connecting clients.
 *
 * <p>Supports two verification layers:
 * <ol>
 *   <li>Address marker check during the handshake phase (pre-login)</li>
 *   <li>Custom payload exchange during the play phase (detailed version negotiation)</li>
 * </ol>
 *
 * <p>The {@link #requireAlloyClient} flag (default: {@code true}) controls whether
 * vanilla clients are rejected. Configurable via system property
 * {@code -Dalloy.requireAlloyClient=false}.
 */
public class AlloyHandshakeHandler {

    private static final Gson GSON = new Gson();

    // --- Disconnect messages (Minecraft JSON text component format) ---

    /** Sent to vanilla clients that lack the Alloy marker. */
    public static final String DISCONNECT_NOT_ALLOY =
            "{\"text\":\"This server requires Alloy. Download at alloymc.net\",\"color\":\"red\"}";

    /** Sent when client and server Alloy versions have incompatible protocols. */
    public static final String DISCONNECT_VERSION_MISMATCH =
            "{\"text\":\"Alloy version mismatch. Server: %s, Client: %s\",\"color\":\"red\"}";

    /** Shown client-side when connecting to a non-Alloy server. */
    public static final String DISCONNECT_NOT_ALLOY_SERVER =
            "This is not an Alloy server. Only connect to Alloy-enabled servers.";

    /** Whether to require connecting clients to be Alloy clients. Default: true. */
    private volatile boolean requireAlloyClient;

    public AlloyHandshakeHandler() {
        // Read from system property, default to true
        this.requireAlloyClient = !"false".equalsIgnoreCase(
                System.getProperty("alloy.requireAlloyClient", "true"));
    }

    /**
     * Processes an incoming handshake payload from a client.
     *
     * @param playerId the connecting player's UUID
     * @param json     the JSON-encoded handshake payload
     * @return true if the handshake is accepted, false to reject the connection
     */
    public boolean handleIncoming(UUID playerId, String json) {
        try {
            AlloyHandshakePayload payload = GSON.fromJson(json, AlloyHandshakePayload.class);

            if (payload.protocolVersion() != AlloyHandshakePayload.CURRENT_PROTOCOL) {
                System.out.println("[Alloy] Handshake protocol mismatch from " + playerId
                        + ": expected " + AlloyHandshakePayload.CURRENT_PROTOCOL
                        + ", got " + payload.protocolVersion());
                return !requireAlloyClient;
            }

            System.out.println("[Alloy] Alloy client connected: " + playerId
                    + " (v" + payload.version() + ")");
            return true;

        } catch (Exception e) {
            System.out.println("[Alloy] Invalid handshake from " + playerId
                    + ": " + e.getMessage());
            return !requireAlloyClient;
        }
    }

    /**
     * Creates the server's handshake payload as JSON.
     */
    public String createPayload() {
        return GSON.toJson(AlloyHandshakePayload.current());
    }

    /**
     * Generates a version mismatch disconnect message with the server and client versions.
     */
    public static String versionMismatchMessage(String clientVersion) {
        return String.format(DISCONNECT_VERSION_MISMATCH, AlloyLoader.VERSION, clientVersion);
    }

    public boolean isRequireAlloyClient() {
        return requireAlloyClient;
    }

    public void setRequireAlloyClient(boolean requireAlloyClient) {
        this.requireAlloyClient = requireAlloyClient;
    }
}
