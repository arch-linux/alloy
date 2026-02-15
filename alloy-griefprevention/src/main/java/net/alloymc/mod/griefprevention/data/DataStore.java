package net.alloymc.mod.griefprevention.data;

import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimManager;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.util.UUID;

/**
 * Abstract persistence layer for GriefPrevention data.
 * Implementations handle reading/writing claims and player data to storage.
 */
public interface DataStore {

    /**
     * Load all claims and player data from storage into the ClaimManager.
     */
    void loadAll(ClaimManager claimManager);

    /**
     * Save a single claim to storage.
     */
    void saveClaim(Claim claim);

    /**
     * Delete a claim from storage.
     */
    void deleteClaim(Claim claim);

    /**
     * Save player data to storage.
     */
    void savePlayerData(UUID playerID, PlayerData data);

    /**
     * Load player data from storage.
     */
    PlayerData loadPlayerData(UUID playerID);

    /**
     * Save all in-memory data to storage.
     */
    void saveAll(ClaimManager claimManager);

    /**
     * Save the next claim ID counter.
     */
    void saveNextClaimId(long nextId);

    /**
     * Close resources.
     */
    void close();
}
