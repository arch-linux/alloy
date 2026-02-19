package net.alloymc.core.handshake;

import net.alloymc.loader.AlloyLoader;

/**
 * Payload for the Alloy client-server version handshake.
 *
 * <p>Two layers of verification:
 * <ol>
 *   <li><b>Address marker</b>: The client appends {@code \0ALLOY\0version\0protocol} to
 *       the server address in the initial handshake packet (same technique as Forge/FML).
 *       The server checks this marker during the handshake phase.</li>
 *   <li><b>Custom payload</b>: After entering the play phase, both sides exchange
 *       JSON payloads over the {@link #CHANNEL} plugin channel for detailed version
 *       negotiation.</li>
 * </ol>
 *
 * @param version         the Alloy loader version (e.g., "0.1.0")
 * @param protocolVersion the handshake protocol version
 */
public record AlloyHandshakePayload(String version, int protocolVersion) {

    /** Custom channel name for the handshake. */
    public static final String CHANNEL = "alloy:handshake";

    /** Current handshake protocol version. */
    public static final int CURRENT_PROTOCOL = 1;

    /** Marker appended to the server address in the initial handshake packet. */
    public static final String ADDRESS_MARKER = "\0ALLOY\0";

    /**
     * Creates a payload for the current Alloy version.
     */
    public static AlloyHandshakePayload current() {
        return new AlloyHandshakePayload(AlloyLoader.VERSION, CURRENT_PROTOCOL);
    }

    /**
     * Parses an Alloy handshake payload from a server address field.
     * The address format is: {@code hostname\0ALLOY\0version\0protocol}
     *
     * @param address the raw address from the handshake packet
     * @return the parsed payload, or {@code null} if the address has no Alloy marker
     */
    public static AlloyHandshakePayload fromAddress(String address) {
        if (address == null) return null;
        int idx = address.indexOf(ADDRESS_MARKER);
        if (idx < 0) return null;
        String data = address.substring(idx + ADDRESS_MARKER.length());
        String[] parts = data.split("\0");
        if (parts.length < 2) return null;
        try {
            return new AlloyHandshakePayload(parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Appends the Alloy marker to a server address for the handshake packet.
     *
     * @param address the original server address
     * @return the address with the Alloy marker appended
     */
    public static String appendToAddress(String address) {
        return address + ADDRESS_MARKER + AlloyLoader.VERSION + "\0" + CURRENT_PROTOCOL;
    }

    /**
     * Strips the Alloy marker from a server address, returning the original hostname.
     *
     * @param address the address potentially containing an Alloy marker
     * @return the clean address without the marker
     */
    public static String stripMarker(String address) {
        if (address == null) return null;
        int idx = address.indexOf(ADDRESS_MARKER);
        return idx < 0 ? address : address.substring(0, idx);
    }
}
