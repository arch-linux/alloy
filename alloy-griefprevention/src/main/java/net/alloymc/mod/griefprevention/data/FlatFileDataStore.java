package net.alloymc.mod.griefprevention.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alloymc.mod.griefprevention.claim.Claim;
import net.alloymc.mod.griefprevention.claim.ClaimManager;
import net.alloymc.mod.griefprevention.claim.ClaimPermission;
import net.alloymc.mod.griefprevention.player.PlayerData;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flat-file data store. Stores claims and player data as individual JSON files.
 *
 * <pre>
 * dataDir/
 *   claims/
 *     {claimId}.json
 *   players/
 *     {uuid}.json
 *   _nextClaimId.txt
 * </pre>
 */
public class FlatFileDataStore implements DataStore {

    private static final Logger LOGGER = Logger.getLogger("GriefPrevention.DataStore");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataDir;
    private final Path claimsDir;
    private final Path playersDir;
    private final Path nextIdFile;

    public FlatFileDataStore(Path dataDir) {
        this.dataDir = dataDir;
        this.claimsDir = dataDir.resolve("claims");
        this.playersDir = dataDir.resolve("players");
        this.nextIdFile = dataDir.resolve("_nextClaimId.txt");

        try {
            Files.createDirectories(claimsDir);
            Files.createDirectories(playersDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create GriefPrevention data directories at " + dataDir, e);
        }
    }

    @Override
    public void loadAll(ClaimManager claimManager) {
        // Load next claim ID
        if (Files.exists(nextIdFile)) {
            try {
                String content = Files.readString(nextIdFile).trim();
                long nextId = Long.parseLong(content);
                claimManager.nextClaimIdRef().set(nextId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to read next claim ID, starting from 1", e);
            }
        }

        // Load claims
        int claimCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(claimsDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String json = Files.readString(file);
                    Claim claim = deserializeClaim(json);
                    if (claim != null) {
                        claimManager.addLoadedClaim(claim);
                        claimCount++;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to load claim from " + file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read claims directory", e);
        }

        // Load player data
        int playerCount = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDir, "*.json")) {
            for (Path file : stream) {
                try {
                    String name = file.getFileName().toString().replace(".json", "");
                    UUID playerID = UUID.fromString(name);
                    String json = Files.readString(file);
                    PlayerData data = deserializePlayerData(playerID, json);
                    if (data != null) {
                        PlayerData existing = claimManager.getPlayerData(playerID);
                        existing.setAccruedClaimBlocks(data.accruedClaimBlocks());
                        existing.setBonusClaimBlocks(data.bonusClaimBlocks());
                        existing.setSoftMuted(data.isSoftMuted());
                        playerCount++;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to load player data from " + file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read players directory", e);
        }

        LOGGER.info("Loaded " + claimCount + " claims and " + playerCount + " player records.");
    }

    @Override
    public void saveClaim(Claim claim) {
        Path file = claimsDir.resolve(claim.id() + ".json");
        try {
            Files.writeString(file, serializeClaim(claim));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save claim " + claim.id(), e);
        }
    }

    @Override
    public void deleteClaim(Claim claim) {
        Path file = claimsDir.resolve(claim.id() + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete claim file " + claim.id(), e);
        }
        // Also delete subclaim files
        for (Claim child : claim.children()) {
            Path childFile = claimsDir.resolve(child.id() + ".json");
            try {
                Files.deleteIfExists(childFile);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete subclaim file " + child.id(), e);
            }
        }
    }

    @Override
    public void savePlayerData(UUID playerID, PlayerData data) {
        Path file = playersDir.resolve(playerID + ".json");
        try {
            Files.writeString(file, serializePlayerData(data));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save player data for " + playerID, e);
        }
    }

    @Override
    public PlayerData loadPlayerData(UUID playerID) {
        Path file = playersDir.resolve(playerID + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            return deserializePlayerData(playerID, json);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load player data for " + playerID, e);
            return null;
        }
    }

    @Override
    public void saveAll(ClaimManager claimManager) {
        // Save all claims
        for (Claim claim : claimManager.allClaims()) {
            saveClaim(claim);
            for (Claim child : claim.children()) {
                saveClaim(child);
            }
        }

        // Save all player data
        for (Map.Entry<UUID, PlayerData> entry : claimManager.allPlayerData().entrySet()) {
            savePlayerData(entry.getKey(), entry.getValue());
        }

        // Save next claim ID
        saveNextClaimId(claimManager.nextClaimIdRef().get());
    }

    @Override
    public void saveNextClaimId(long nextId) {
        try {
            Files.writeString(nextIdFile, String.valueOf(nextId));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save next claim ID", e);
        }
    }

    @Override
    public void close() {
        // No resources to close for flat file
    }

    // ---- Serialization ----

    private String serializeClaim(Claim claim) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", claim.id());
        obj.addProperty("owner", claim.ownerID() != null ? claim.ownerID().toString() : null);
        obj.addProperty("world", claim.worldName());
        obj.addProperty("lesserX", claim.lesserX());
        obj.addProperty("lesserY", claim.lesserY());
        obj.addProperty("lesserZ", claim.lesserZ());
        obj.addProperty("greaterX", claim.greaterX());
        obj.addProperty("greaterY", claim.greaterY());
        obj.addProperty("greaterZ", claim.greaterZ());
        obj.addProperty("explosivesAllowed", claim.areExplosivesAllowed());
        obj.addProperty("inheritNothing", claim.inheritNothing());
        obj.addProperty("modified", claim.modifiedDate().toString());

        if (claim.parent() != null) {
            obj.addProperty("parentId", claim.parent().id());
        }

        // Permissions
        JsonObject perms = new JsonObject();
        for (Map.Entry<String, ClaimPermission> entry : claim.permissions().entrySet()) {
            perms.addProperty(entry.getKey(), entry.getValue().name());
        }
        obj.add("permissions", perms);

        // Managers
        JsonArray mgrs = new JsonArray();
        for (String m : claim.managers()) {
            mgrs.add(m);
        }
        obj.add("managers", mgrs);

        // Subclaims
        JsonArray children = new JsonArray();
        for (Claim child : claim.children()) {
            children.add(JsonParser.parseString(serializeClaim(child)));
        }
        obj.add("children", children);

        return GSON.toJson(obj);
    }

    private Claim deserializeClaim(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        long id = obj.get("id").getAsLong();
        JsonElement ownerElem = obj.get("owner");
        UUID owner = (ownerElem != null && !ownerElem.isJsonNull()) ? UUID.fromString(ownerElem.getAsString()) : null;
        String world = obj.get("world").getAsString();

        Claim claim = new Claim(id, owner, world,
                obj.get("lesserX").getAsInt(), obj.get("lesserY").getAsInt(), obj.get("lesserZ").getAsInt(),
                obj.get("greaterX").getAsInt(), obj.get("greaterY").getAsInt(), obj.get("greaterZ").getAsInt(),
                null);

        if (obj.has("explosivesAllowed")) claim.setExplosivesAllowed(obj.get("explosivesAllowed").getAsBoolean());
        if (obj.has("inheritNothing")) claim.setInheritNothing(obj.get("inheritNothing").getAsBoolean());
        if (obj.has("modified")) claim.setModifiedDate(Instant.parse(obj.get("modified").getAsString()));

        // Permissions
        if (obj.has("permissions")) {
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("permissions").entrySet()) {
                try {
                    claim.setPermission(entry.getKey(), ClaimPermission.valueOf(entry.getValue().getAsString()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Managers
        if (obj.has("managers")) {
            for (JsonElement elem : obj.getAsJsonArray("managers")) {
                claim.addManager(elem.getAsString());
            }
        }

        // Subclaims
        if (obj.has("children")) {
            for (JsonElement elem : obj.getAsJsonArray("children")) {
                Claim child = deserializeClaim(elem.toString());
                if (child != null) {
                    child.setParent(claim);
                    claim.children().add(child);
                }
            }
        }

        return claim;
    }

    private String serializePlayerData(PlayerData data) {
        JsonObject obj = new JsonObject();
        obj.addProperty("playerID", data.playerID().toString());
        obj.addProperty("accruedClaimBlocks", data.accruedClaimBlocks());
        obj.addProperty("bonusClaimBlocks", data.bonusClaimBlocks());
        obj.addProperty("softMuted", data.isSoftMuted());
        return GSON.toJson(obj);
    }

    private PlayerData deserializePlayerData(UUID playerID, String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        PlayerData data = new PlayerData(playerID);
        data.setAccruedClaimBlocks(obj.has("accruedClaimBlocks") ? obj.get("accruedClaimBlocks").getAsInt() : 0);
        data.setBonusClaimBlocks(obj.has("bonusClaimBlocks") ? obj.get("bonusClaimBlocks").getAsInt() : 0);
        data.setSoftMuted(obj.has("softMuted") && obj.get("softMuted").getAsBoolean());
        return data;
    }
}
