package net.alloymc.api.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Alloy event bus. Handles registration of listeners and dispatching of events.
 * Thread-safe.
 */
public class EventBus {

    private static final Logger LOGGER = Logger.getLogger("AlloyEventBus");

    private record RegisteredHandler(
            Listener listener,
            Method method,
            EventPriority priority,
            boolean ignoreCancelled,
            Class<? extends Event> eventType
    ) {}

    private final Map<Class<? extends Event>, List<RegisteredHandler>> handlers = new ConcurrentHashMap<>();

    /**
     * Register all @EventHandler methods in the given listener.
     */
    public void register(Listener listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 1) {
                LOGGER.warning("@EventHandler method " + method.getName() + " in "
                        + listener.getClass().getName() + " must have exactly one parameter. Skipping.");
                continue;
            }

            Class<?> paramType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(paramType)) {
                LOGGER.warning("@EventHandler method " + method.getName() + " in "
                        + listener.getClass().getName() + " parameter must extend Event. Skipping.");
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) paramType;
            method.setAccessible(true);

            RegisteredHandler handler = new RegisteredHandler(
                    listener, method, annotation.priority(), annotation.ignoreCancelled(), eventType
            );

            handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);

            // Re-sort by priority after adding
            List<RegisteredHandler> list = handlers.get(eventType);
            List<RegisteredHandler> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.comparingInt(h -> h.priority().ordinal()));
            list.clear();
            list.addAll(sorted);
        }
    }

    /**
     * Unregister all handlers for the given listener.
     */
    public void unregister(Listener listener) {
        handlers.values().forEach(list -> list.removeIf(h -> h.listener() == listener));
    }

    /**
     * Fire an event to all registered handlers.
     *
     * @return the event, after all handlers have been called
     */
    public <T extends Event> T fire(T event) {
        List<RegisteredHandler> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return event;
        }

        boolean cancellable = event instanceof Cancellable;

        for (RegisteredHandler handler : eventHandlers) {
            if (cancellable && handler.ignoreCancelled() && ((Cancellable) event).isCancelled()) {
                continue;
            }

            try {
                handler.method().invoke(handler.listener(), event);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error dispatching event " + event.getClass().getSimpleName()
                        + " to " + handler.listener().getClass().getName() + "." + handler.method().getName(), e);
            }
        }

        return event;
    }
}
