package net.alloymc.mod.griefprevention.claim;

/**
 * Result of a claim creation attempt.
 */
public record CreateClaimResult(boolean succeeded, Claim claim, String failureReason) {

    public static CreateClaimResult success(Claim claim) {
        return new CreateClaimResult(true, claim, null);
    }

    public static CreateClaimResult failure(String reason) {
        return new CreateClaimResult(false, null, reason);
    }
}
