package net.alloymc.api.economy;

/**
 * Manages the active {@link EconomyProvider}.
 *
 * <p>Only one provider is active at a time. Any mod can replace the active
 * provider by calling {@link #setProvider(EconomyProvider)}. The previous
 * provider is cleanly shut down via {@code onDisable()} before the new one
 * is activated via {@code onEnable()}.
 *
 * <p>The built-in {@code alloy-core} mod registers a file-based provider
 * by default. Third-party mods (e.g., a MySQL/Redis economy) can override
 * it — all commands that use the economy API will automatically use the
 * new provider.
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
     *
     * <p>Lifecycle:
     * <ol>
     *   <li>If a provider is currently active, its {@code onDisable()} is called</li>
     *   <li>The new provider becomes active</li>
     *   <li>The new provider's {@code onEnable()} is called</li>
     * </ol>
     *
     * <p>All existing code that calls {@code AlloyAPI.economy().provider()} will
     * immediately see the new provider. No re-registration of commands or
     * listeners is needed.
     *
     * @param provider the new economy provider, or null to disable economy
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
