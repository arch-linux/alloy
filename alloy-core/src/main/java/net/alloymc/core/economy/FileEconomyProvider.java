package net.alloymc.core.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.alloymc.api.economy.EconomyProvider;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-backed economy provider that stores player balances in {@code economy.json}.
 *
 * <p>Thread-safe — all balance operations are synchronized on the balances map.
 * Saves to disk after every mutating operation (deposit, withdraw, transfer, setBalance).
 */
public final class FileEconomyProvider implements EconomyProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();

    private final Path file;
    private final double startingBalance;
    private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();

    public FileEconomyProvider(Path dataDir, double startingBalance) {
        this.file = dataDir.resolve("economy.json");
        this.startingBalance = startingBalance;
    }

    @Override
    public void onEnable() {
        load();
    }

    @Override
    public double getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, startingBalance);
    }

    @Override
    public void setBalance(UUID playerId, double amount) {
        if (amount < 0) amount = 0;
        balances.put(playerId, amount);
        save();
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        if (amount <= 0) return;
        balances.merge(playerId, amount, (old, add) -> old + add);
        // If the player had no entry, they start from startingBalance + amount
        balances.computeIfPresent(playerId, (k, v) -> v);
        if (!balances.containsKey(playerId)) {
            balances.put(playerId, startingBalance + amount);
        }
        save();
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) return false;
        synchronized (balances) {
            double current = getBalance(playerId);
            if (current < amount) return false;
            balances.put(playerId, current - amount);
            save();
            return true;
        }
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return getBalance(playerId) >= amount;
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        if (from.equals(to)) return false;
        synchronized (balances) {
            double fromBalance = getBalance(from);
            if (fromBalance < amount) return false;
            double toBalance = getBalance(to);
            balances.put(from, fromBalance - amount);
            balances.put(to, toBalance + amount);
            save();
            return true;
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            System.out.println("[AlloyCore] No economy.json found — starting fresh");
            return;
        }
        try {
            String json = Files.readString(file);
            Map<String, Double> raw = GSON.fromJson(json, MAP_TYPE);
            if (raw != null) {
                raw.forEach((key, value) -> {
                    try {
                        balances.put(UUID.fromString(key), value);
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed UUIDs
                    }
                });
            }
            System.out.println("[AlloyCore] Loaded " + balances.size() + " economy accounts");
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to load economy.json: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Map<String, Double> raw = new ConcurrentHashMap<>();
            balances.forEach((uuid, balance) -> raw.put(uuid.toString(), balance));
            Files.writeString(file, GSON.toJson(raw));
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to save economy.json: " + e.getMessage());
        }
    }
}
