package net.alloymc.mappings.mojang;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Interacts with Mojang's version metadata APIs to discover and download
 * Minecraft client JARs, libraries, mappings, and assets.
 */
public final class MojangApi {

    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    // ---- Records ----

    public record VersionEntry(String id, String type, String metadataUrl) {}

    public record Download(String url, String sha1, long size) {}

    public record Library(String name, Download artifact, List<Rule> rules) {
        public boolean isNative() {
            return name.contains(":natives-");
        }
    }

    public record Rule(String action, String osName, String osArch) {}

    public record AssetIndexInfo(String id, Download download) {}

    public record VersionDetails(
            String id,
            String mainClass,
            Download client,
            Download clientMappings,
            Download server,
            AssetIndexInfo assetIndex,
            List<Library> libraries
    ) {}

    // ---- Client ----

    private final HttpClient httpClient;

    public MojangApi() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    // ---- Version Resolution ----

    public VersionEntry getLatestRelease() throws IOException, InterruptedException {
        String body = fetchString(MANIFEST_URL);
        JsonObject manifest = JsonParser.parseString(body).getAsJsonObject();
        String latestId = manifest.getAsJsonObject("latest").get("release").getAsString();

        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (version.get("id").getAsString().equals(latestId)) {
                return new VersionEntry(
                        latestId,
                        version.get("type").getAsString(),
                        version.get("url").getAsString()
                );
            }
        }
        throw new IOException("Latest release '" + latestId + "' not found in version manifest.");
    }

    public VersionEntry getVersion(String versionId) throws IOException, InterruptedException {
        String body = fetchString(MANIFEST_URL);
        JsonObject manifest = JsonParser.parseString(body).getAsJsonObject();

        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (version.get("id").getAsString().equals(versionId)) {
                return new VersionEntry(
                        versionId,
                        version.get("type").getAsString(),
                        version.get("url").getAsString()
                );
            }
        }
        throw new IOException("Version '" + versionId + "' not found in Mojang's manifest. "
                + "Check the version ID — available versions are listed at: " + MANIFEST_URL);
    }

    // ---- Version Details ----

    public VersionDetails fetchDetails(VersionEntry version)
            throws IOException, InterruptedException {
        String body = fetchString(version.metadataUrl());
        JsonObject metadata = JsonParser.parseString(body).getAsJsonObject();

        // Downloads
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        Download client = parseDownload(downloads, "client");
        Download clientMappings = parseDownload(downloads, "client_mappings");
        Download server = parseDownload(downloads, "server");

        if (client == null) {
            throw new IOException("Minecraft " + version.id() + " has no client JAR download.");
        }
        if (clientMappings == null) {
            throw new IOException("Minecraft " + version.id() + " has no official mappings. "
                    + "Official mappings are only available for 1.14.4+ releases.");
        }

        // Main class
        String mainClass = metadata.get("mainClass").getAsString();

        // Asset index
        JsonObject assetIndexObj = metadata.getAsJsonObject("assetIndex");
        AssetIndexInfo assetIndex = new AssetIndexInfo(
                assetIndexObj.get("id").getAsString(),
                new Download(
                        assetIndexObj.get("url").getAsString(),
                        assetIndexObj.get("sha1").getAsString(),
                        assetIndexObj.get("size").getAsLong()
                )
        );

        // Libraries
        List<Library> libraries = parseLibraries(metadata.getAsJsonArray("libraries"));

        return new VersionDetails(version.id(), mainClass, client, clientMappings, server,
                assetIndex, libraries);
    }

    // ---- Library Parsing ----

    private List<Library> parseLibraries(JsonArray librariesArray) {
        List<Library> libraries = new ArrayList<>();

        for (JsonElement element : librariesArray) {
            JsonObject libObj = element.getAsJsonObject();
            String name = libObj.get("name").getAsString();

            // Parse artifact download
            Download artifact = null;
            if (libObj.has("downloads")) {
                JsonObject dlObj = libObj.getAsJsonObject("downloads");
                if (dlObj.has("artifact")) {
                    JsonObject artObj = dlObj.getAsJsonObject("artifact");
                    artifact = new Download(
                            artObj.get("url").getAsString(),
                            artObj.get("sha1").getAsString(),
                            artObj.get("size").getAsLong()
                    );
                }
            }

            // Parse rules
            List<Rule> rules = new ArrayList<>();
            if (libObj.has("rules")) {
                for (JsonElement ruleEl : libObj.getAsJsonArray("rules")) {
                    JsonObject ruleObj = ruleEl.getAsJsonObject();
                    String action = ruleObj.get("action").getAsString();
                    String osName = null;
                    String osArch = null;

                    if (ruleObj.has("os")) {
                        JsonObject osObj = ruleObj.getAsJsonObject("os");
                        if (osObj.has("name")) osName = osObj.get("name").getAsString();
                        if (osObj.has("arch")) osArch = osObj.get("arch").getAsString();
                    }

                    rules.add(new Rule(action, osName, osArch));
                }
            }

            if (artifact != null) {
                libraries.add(new Library(name, artifact, rules));
            }
        }

        return libraries;
    }

    /**
     * Filters libraries to only those matching the current platform.
     */
    public static List<Library> filterForPlatform(List<Library> libraries) {
        String osName = detectOsName();
        String osArch = detectOsArch();

        List<Library> result = new ArrayList<>();
        for (Library lib : libraries) {
            if (shouldInclude(lib.rules(), osName, osArch)) {
                result.add(lib);
            }
        }
        return result;
    }

    private static boolean shouldInclude(List<Rule> rules, String osName, String osArch) {
        if (rules.isEmpty()) return true;

        boolean include = false;
        for (Rule rule : rules) {
            boolean matches = true;
            if (rule.osName() != null && !rule.osName().equals(osName)) {
                matches = false;
            }
            if (rule.osArch() != null && !rule.osArch().equals(osArch)) {
                matches = false;
            }
            if (matches) {
                include = "allow".equals(rule.action());
            }
        }
        return include;
    }

    private static String detectOsName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        if (os.contains("win")) return "windows";
        return "linux";
    }

    private static String detectOsArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("64")) return "x86_64";
        return "x86";
    }

    // ---- Downloading ----

    public Path download(Download dl, Path destination)
            throws IOException, InterruptedException {
        if (Files.exists(destination) && verifySha1(destination, dl.sha1())) {
            return destination;
        }

        Files.createDirectories(destination.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(dl.url())).build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination));

        if (!verifySha1(destination, dl.sha1())) {
            Files.delete(destination);
            throw new IOException("SHA1 mismatch for " + destination.getFileName()
                    + ". Expected: " + dl.sha1());
        }

        return destination;
    }

    /**
     * Downloads a file without SHA1 verification (for assets where we trust the hash as filename).
     */
    public Path downloadRaw(String url, Path destination)
            throws IOException, InterruptedException {
        if (Files.exists(destination)) {
            return destination;
        }

        Files.createDirectories(destination.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofFile(destination));

        return destination;
    }

    public String fetchString(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }
        return response.body();
    }

    // ---- Utilities ----

    private static Download parseDownload(JsonObject downloads, String key) {
        if (!downloads.has(key)) return null;
        JsonObject dl = downloads.getAsJsonObject(key);
        return new Download(
                dl.get("url").getAsString(),
                dl.get("sha1").getAsString(),
                dl.get("size").getAsLong()
        );
    }

    public static boolean verifySha1(Path file, String expectedSha1) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(fileBytes);
            String actual = HexFormat.of().formatHex(hash);
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
