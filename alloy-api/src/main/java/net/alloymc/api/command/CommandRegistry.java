package net.alloymc.api.command;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages command registration and lookup.
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new ConcurrentHashMap<>();

    /**
     * Register a command. Also registers all aliases.
     */
    public void register(Command command) {
        commands.put(command.name().toLowerCase(), command);
        for (String alias : command.aliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    /**
     * Look up a command by name or alias.
     */
    public Command get(String name) {
        return commands.get(name.toLowerCase());
    }

    /**
     * Returns all registered commands (may include alias duplicates).
     */
    public Collection<Command> all() {
        return Collections.unmodifiableCollection(commands.values());
    }
}
