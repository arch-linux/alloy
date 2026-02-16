package net.alloymc.api.economy;

import java.util.UUID;

/**
 * Provides economy operations for player balances.
 *
 * <p>Implementations handle persistent storage (file, database, etc.).
 * Only one provider is active at a time — the last registered wins.
 */
public interface EconomyProvider {

    /**
     * Returns the player's current balance.
     *
     * @param playerId the player's UUID
     * @return the balance, or 0.0 if the player has no account
     */
    double getBalance(UUID playerId);

    /**
     * Sets the player's balance to an exact value.
     *
     * @param playerId the player's UUID
     * @param amount   the new balance (must be >= 0)
     */
    void setBalance(UUID playerId, double amount);

    /**
     * Adds money to a player's balance.
     *
     * @param playerId the player's UUID
     * @param amount   the amount to add (must be > 0)
     */
    void deposit(UUID playerId, double amount);

    /**
     * Removes money from a player's balance.
     *
     * @param playerId the player's UUID
     * @param amount   the amount to remove (must be > 0)
     * @return true if the player had enough funds and the withdrawal succeeded
     */
    boolean withdraw(UUID playerId, double amount);

    /**
     * Checks whether the player has at least the given amount.
     *
     * @param playerId the player's UUID
     * @param amount   the amount to check
     * @return true if balance >= amount
     */
    boolean has(UUID playerId, double amount);

    /**
     * Transfers money from one player to another atomically.
     *
     * @param from   the sender's UUID
     * @param to     the receiver's UUID
     * @param amount the amount to transfer (must be > 0)
     * @return true if the sender had enough funds and the transfer succeeded
     */
    boolean transfer(UUID from, UUID to, double amount);

    /** Called when this provider becomes active. */
    default void onEnable() {}

    /** Called when this provider is replaced by another. */
    default void onDisable() {}
}
