package net.alloymc.core.handshake;

import com.google.gson.Gson;

import java.util.UUID;

/**
 * Handles incoming Alloy handshake payloads from connecting clients.
 *
 * <p>This is a skeleton for Phase 1. Full wire protocol integration
 * comes when we have the server connection handler injection points mapped.
 */
public class AlloyHandshakeHandler {

    private static final Gson GSON = new Gson();

    /** Whether to require connecting clients to be Alloy clients. Default: false. */
    private volatile boolean requireAlloyClient = false;

    public AlloyHandshakeHandler() {}

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
                System.out.println("[AlloyCore] Handshake protocol mismatch from " + playerId
                        + ": expected " + AlloyHandshakePayload.CURRENT_PROTOCOL
                        + ", got " + payload.protocolVersion());
                return !requireAlloyClient;
            }

            System.out.println("[AlloyCore] Alloy client connected: " + playerId
                    + " (v" + payload.version() + ")");
            return true;

        } catch (Exception e) {
            System.out.println("[AlloyCore] Invalid handshake from " + playerId
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

    public boolean isRequireAlloyClient() {
        return requireAlloyClient;
    }

    public void setRequireAlloyClient(boolean requireAlloyClient) {
        this.requireAlloyClient = requireAlloyClient;
    }
}
