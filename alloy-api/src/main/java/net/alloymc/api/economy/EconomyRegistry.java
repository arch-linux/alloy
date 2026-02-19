package net.alloymc.api.economy;

/**
 * Manages the active {@link EconomyProvider} and economy display settings.
 *
 * <p>Only one provider is active at a time. Any mod can replace the active
 * provider by calling {@link #setProvider(EconomyProvider)}.
 *
 * <p>The currency symbol defaults to "$" but can be overridden by economy
 * mods (e.g., DilithiumEconomy sets it to "Ð").
 */
public class EconomyRegistry {

    private volatile EconomyProvider provider;
    private volatile String currencySymbol = "$";

    /**
     * Returns the active economy provider, or null if none is registered.
     */
    public EconomyProvider provider() {
        return provider;
    }

    /**
     * Returns true if an economy provider is available.
     */
    public boolean isAvailable() {
        return provider != null;
    }

    /**
     * Returns the currency symbol used for display (default: "$").
     * Economy mods can override this via {@link #setCurrencySymbol(String)}.
     */
    public String currencySymbol() {
        return currencySymbol;
    }

    /**
     * Sets the currency symbol for display purposes.
     * Called by economy mods to match their currency (e.g., "Ð" for Dilithium).
     *
     * @param symbol the currency symbol string
     */
    public void setCurrencySymbol(String symbol) {
        this.currencySymbol = symbol;
        System.out.println("[Alloy] Currency symbol set to: " + symbol);
    }

    /**
     * Formats an amount with the currency symbol.
     * E.g., formatAmount(10.50) → "Ð10.50" or "$10.50"
     */
    public String formatAmount(double amount) {
        return currencySymbol + String.format("%.2f", amount);
    }

    /**
     * Replaces the active economy provider.
     */
    public void setProvider(EconomyProvider provider) {
        EconomyProvider old = this.provider;
        if (old != null) {
            System.out.println("[Alloy] Economy provider " + old.getClass().getSimpleName()
                    + " is being replaced by "
                    + (provider != null ? provider.getClass().getSimpleName() : "null"));
            old.onDisable();
        }
        this.provider = provider;
        if (provider != null) {
            provider.onEnable();
            System.out.println("[Alloy] Economy provider active: " + provider.getClass().getSimpleName());
        }
    }
}
