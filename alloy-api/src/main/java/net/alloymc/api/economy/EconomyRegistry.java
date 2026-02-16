package net.alloymc.api.economy;

/**
 * Manages the active {@link EconomyProvider}.
 * Only one provider is active at a time — the last registered wins.
 */
public class EconomyRegistry {

    private volatile EconomyProvider provider;

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
     * Replaces the active economy provider.
     * Calls {@code onDisable()} on the old provider and {@code onEnable()} on the new one.
     */
    public void setProvider(EconomyProvider provider) {
        EconomyProvider old = this.provider;
        if (old != null) {
            old.onDisable();
        }
        this.provider = provider;
        if (provider != null) {
            provider.onEnable();
        }
    }
}
