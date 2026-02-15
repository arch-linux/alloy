package net.alloymc.api.command;

import java.util.Collections;
import java.util.List;

/**
 * A command that can be executed by players or console.
 * Mods create subclasses of this and register them with the CommandRegistry.
 */
public abstract class Command {

    private final String name;
    private final String description;
    private final String permission;
    private final List<String> aliases;

    protected Command(String name, String description, String permission, List<String> aliases) {
        this.name = name;
        this.description = description;
        this.permission = permission;
        this.aliases = aliases != null ? List.copyOf(aliases) : Collections.emptyList();
    }

    protected Command(String name, String description, String permission) {
        this(name, description, permission, Collections.emptyList());
    }

    public String name() { return name; }
    public String description() { return description; }
    public String permission() { return permission; }
    public List<String> aliases() { return aliases; }

    /**
     * Execute this command.
     *
     * @param sender who executed the command
     * @param label  the alias used to invoke the command
     * @param args   the arguments passed
     * @return true if the command was handled
     */
    public abstract boolean execute(CommandSender sender, String label, String[] args);

    /**
     * Tab-complete this command.
     *
     * @return list of completions, or empty list
     */
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        return Collections.emptyList();
    }
}
