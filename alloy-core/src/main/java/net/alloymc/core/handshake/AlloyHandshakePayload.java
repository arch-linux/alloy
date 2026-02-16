package net.alloymc.core.handshake;

import net.alloymc.loader.AlloyLoader;

/**
 * Payload for the Alloy client-server version handshake.
 *
 * <p>Sent as JSON over the custom plugin channel during login.
 * Full wire protocol implementation depends on runtime bytecode injection
 * into the server's connection handler (Phase 2).
 *
 * @param version         the Alloy loader version (e.g., "0.1.0")
 * @param protocolVersion the handshake protocol version
 */
public record AlloyHandshakePayload(String version, int protocolVersion) {

    /** Custom channel name for the handshake. */
    public static final String CHANNEL = "alloy:handshake";

    /** Current handshake protocol version. */
    public static final int CURRENT_PROTOCOL = 1;

    /**
     * Creates a payload for the current Alloy version.
     */
    public static AlloyHandshakePayload current() {
        return new AlloyHandshakePayload(AlloyLoader.VERSION, CURRENT_PROTOCOL);
    }
}
