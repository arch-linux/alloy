package net.alloymc.api.event;

/**
 * Base class for all events in the Alloy event system.
 * Events are fired by the game engine and can be listened to by mods.
 */
public abstract class Event {

    private final boolean async;

    protected Event() {
        this(false);
    }

    protected Event(boolean async) {
        this.async = async;
    }

    /**
     * Whether this event fires asynchronously (off the main thread).
     */
    public boolean isAsync() {
        return async;
    }
}
