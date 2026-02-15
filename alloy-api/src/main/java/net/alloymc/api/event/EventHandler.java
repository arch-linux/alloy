package net.alloymc.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. The method must accept exactly one parameter
 * that extends {@link Event}.
 *
 * <pre>
 * {@code
 * @EventHandler(priority = EventPriority.NORMAL)
 * public void onBlockBreak(BlockBreakEvent event) {
 *     // handle event
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {

    EventPriority priority() default EventPriority.NORMAL;

    /**
     * If true, this handler will not be called for events that have already been cancelled.
     */
    boolean ignoreCancelled() default false;
}
