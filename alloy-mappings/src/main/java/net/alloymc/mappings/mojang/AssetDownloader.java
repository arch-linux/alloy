package net.alloymc.mappings.mojang;

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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads Minecraft's game assets (textures, sounds, language files, etc.)
 * using parallel connections for performance.
 *
 * <p>Assets are stored in Mojang's content-addressed format:
 * {@code assets/objects/<first2chars>/<full_hash>}
 */
public final class AssetDownloader {

    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final int MAX_CONCURRENT_DOWNLOADS = 32;

    private AssetDownloader() {}

    /**
     * Downloads all game assets for a version.
     *
     * @param api        the Mojang API client
     * @param assetIndex asset index info from version details
     * @param assetsDir  the root assets directory (will contain indexes/ and objects/)
     */
    public static void downloadAll(MojangApi api, MojangApi.AssetIndexInfo assetIndex,
                                    Path assetsDir) throws Exception {
        // Download the asset index JSON
        Path indexesDir = assetsDir.resolve("indexes");
        Files.createDirectories(indexesDir);
        Path indexFile = indexesDir.resolve(assetIndex.id() + ".json");
        api.download(assetIndex.download(), indexFile);

        // Parse the index
        String indexJson = Files.readString(indexFile);
        JsonObject root = JsonParser.parseString(indexJson).getAsJsonObject();
        JsonObject objects = root.getAsJsonObject("objects");

        int totalAssets = objects.size();
        Path objectsDir = assetsDir.resolve("objects");
        Files.createDirectories(objectsDir);

        // Count how many already exist
        AtomicInteger cached = new AtomicInteger(0);
        AtomicInteger downloaded = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path target = objectsDir.resolve(prefix).resolve(hash);
            if (Files.exists(target)) {
                cached.incrementAndGet();
            }
        }

        int toDownload = totalAssets - cached.get();
        if (toDownload == 0) {
            System.out.println("[Alloy] Assets: " + totalAssets + " files (all cached)");
            return;
        }

        System.out.println("[Alloy] Assets: " + totalAssets + " total, "
                + cached.get() + " cached, " + toDownload + " to download");

        // Download missing assets with bounded parallelism
        HttpClient httpClient = api.getHttpClient();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_DOWNLOADS);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
                String prefix = hash.substring(0, 2);
                Path targetDir = objectsDir.resolve(prefix);
                Path target = targetDir.resolve(hash);

                if (Files.exists(target)) continue;

                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            Files.createDirectories(targetDir);
                            String url = RESOURCES_URL + prefix + "/" + hash;
                            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
                            httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));

                            int done = downloaded.incrementAndGet();
                            if (done % 500 == 0 || done == toDownload) {
                                System.out.println("[Alloy] Assets: " + done + "/" + toDownload);
                            }
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                });
            }
        }

        if (failed.get() > 0) {
            System.out.println("[Alloy] WARNING: " + failed.get()
                    + " asset(s) failed to download. Re-run setupWorkspace to retry.");
        }

        System.out.println("[Alloy] Assets complete: " + downloaded.get() + " downloaded");
    }
}
