package net.alloymc.mod.griefprevention.claim;

/**
 * Permission levels within a claim, ordered from highest to lowest.
 * Higher permissions grant all lower permissions automatically.
 *
 * <ul>
 *   <li>EDIT — Owner-only. Full control over the claim.</li>
 *   <li>MANAGE — Can grant/revoke permissions. Includes Build + Container + Access.</li>
 *   <li>BUILD — Can break/place blocks. Includes Container + Access.</li>
 *   <li>CONTAINER — Can open containers, interact with animals/crops. Includes Access.</li>
 *   <li>ACCESS — Can use doors, buttons, levers, beds.</li>
 * </ul>
 */
public enum ClaimPermission {

    EDIT("Only the claim owner can do that."),
    MANAGE("You don't have permission to manage this claim."),
    BUILD("You don't have permission to build here."),
    CONTAINER("You don't have permission to use containers here."),
    ACCESS("You don't have permission to access this here.");

    private final String denialMessage;

    ClaimPermission(String denialMessage) {
        this.denialMessage = denialMessage;
    }

    public String denialMessage() {
        return denialMessage;
    }

    /**
     * Returns true if this permission level is granted by the given permission.
     * A higher permission always grants lower permissions.
     * For example, BUILD.isGrantedBy(MANAGE) returns true.
     */
    public boolean isGrantedBy(ClaimPermission granted) {
        // Lower ordinal = higher permission. If the granted permission
        // has a lower or equal ordinal, it grants this permission.
        return granted.ordinal() <= this.ordinal();
    }
}
