package net.alloymc.core.command;

import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;
import net.alloymc.core.permission.FilePermissionProvider;
import net.alloymc.core.permission.PermissionConfig;
import net.alloymc.core.permission.PermissionGroup;
import net.alloymc.core.permission.PermissionUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages groups and users in the file-based permission system.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>/permissions reload — reload permissions.json</li>
 *   <li>/permissions groups — list all groups</li>
 *   <li>/permissions group &lt;name&gt; — show group details</li>
 *   <li>/permissions user &lt;name&gt; — show user permissions</li>
 *   <li>/permissions user &lt;name&gt; addgroup &lt;group&gt; — add user to group</li>
 *   <li>/permissions user &lt;name&gt; removegroup &lt;group&gt; — remove user from group</li>
 *   <li>/permissions user &lt;name&gt; set &lt;permission&gt; &lt;true|false&gt; — set user permission</li>
 * </ul>
 */
public class PermissionCommand extends Command {

    private final FilePermissionProvider provider;

    public PermissionCommand(FilePermissionProvider provider) {
        super("permissions", "Manage permission groups and users", "alloy.command.permissions",
                List.of("perms"));
        this.provider = provider;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "groups" -> handleGroups(sender);
            case "group" -> handleGroup(sender, args);
            case "user" -> handleUser(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        provider.reload();
        sender.sendMessage("[Alloy] Permissions reloaded from disk.");
        return true;
    }

    private boolean handleGroups(CommandSender sender) {
        PermissionConfig config = provider.getConfig();
        sender.sendMessage("[Alloy] Permission groups:");
        for (Map.Entry<String, PermissionGroup> entry : config.groups().entrySet()) {
            PermissionGroup group = entry.getValue();
            sender.sendMessage("  - " + entry.getKey()
                    + " (" + group.permissions().size() + " permissions"
                    + (group.parents().isEmpty() ? "" : ", inherits: " + String.join(", ", group.parents()))
                    + ")");
        }
        return true;
    }

    private boolean handleGroup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[Alloy] Usage: /permissions group <name>");
            return true;
        }

        String groupName = args[1];
        PermissionConfig config = provider.getConfig();
        PermissionGroup group = config.groups().get(groupName);

        if (group == null) {
            sender.sendMessage("[Alloy] Group '" + groupName + "' does not exist.");
            return true;
        }

        sender.sendMessage("[Alloy] Group: " + groupName);
        sender.sendMessage("  Parents: " + (group.parents().isEmpty() ? "none" : String.join(", ", group.parents())));
        sender.sendMessage("  Permissions:");
        for (String perm : group.permissions()) {
            sender.sendMessage("    - " + perm);
        }
        return true;
    }

    private boolean handleUser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[Alloy] Usage: /permissions user <name> [addgroup|removegroup|set] ...");
            return true;
        }

        String userName = args[1];

        if (args.length == 2) {
            // Show user info
            PermissionConfig config = provider.getConfig();
            PermissionUser user = findUser(config, userName);
            if (user == null) {
                sender.sendMessage("[Alloy] No permission data for user '" + userName + "'.");
                return true;
            }
            sender.sendMessage("[Alloy] User: " + userName);
            sender.sendMessage("  Groups: " + (user.groups().isEmpty() ? "default" : String.join(", ", user.groups())));
            sender.sendMessage("  Op: " + user.op());
            if (!user.permissions().isEmpty()) {
                sender.sendMessage("  Overrides:");
                for (Map.Entry<String, Boolean> entry : user.permissions().entrySet()) {
                    sender.sendMessage("    - " + entry.getKey() + ": " + entry.getValue());
                }
            }
            return true;
        }

        String action = args[2].toLowerCase();
        return switch (action) {
            case "addgroup" -> handleUserAddGroup(sender, userName, args);
            case "removegroup" -> handleUserRemoveGroup(sender, userName, args);
            case "set" -> handleUserSetPermission(sender, userName, args);
            default -> {
                sender.sendMessage("[Alloy] Unknown action: " + action);
                yield true;
            }
        };
    }

    private boolean handleUserAddGroup(CommandSender sender, String userName, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("[Alloy] Usage: /permissions user <name> addgroup <group>");
            return true;
        }
        String group = args[3];
        PermissionConfig config = provider.getConfig();
        if (!config.groups().containsKey(group)) {
            sender.sendMessage("[Alloy] Group '" + group + "' does not exist.");
            return true;
        }

        provider.addUserToGroup(userName, group);
        sender.sendMessage("[Alloy] Added '" + userName + "' to group '" + group + "'.");
        return true;
    }

    private boolean handleUserRemoveGroup(CommandSender sender, String userName, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("[Alloy] Usage: /permissions user <name> removegroup <group>");
            return true;
        }
        String group = args[3];
        provider.removeUserFromGroup(userName, group);
        sender.sendMessage("[Alloy] Removed '" + userName + "' from group '" + group + "'.");
        return true;
    }

    private boolean handleUserSetPermission(CommandSender sender, String userName, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("[Alloy] Usage: /permissions user <name> set <permission> <true|false>");
            return true;
        }
        String permission = args[3];
        boolean value = Boolean.parseBoolean(args[4]);
        provider.setUserPermission(userName, permission, value);
        sender.sendMessage("[Alloy] Set '" + permission + "' = " + value + " for '" + userName + "'.");
        return true;
    }

    private PermissionUser findUser(PermissionConfig config, String userName) {
        for (Map.Entry<String, PermissionUser> entry : config.users().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(userName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("[Alloy] Permission commands:");
        sender.sendMessage("  /permissions reload — Reload from disk");
        sender.sendMessage("  /permissions groups — List all groups");
        sender.sendMessage("  /permissions group <name> — Show group details");
        sender.sendMessage("  /permissions user <name> — Show user permissions");
        sender.sendMessage("  /permissions user <name> addgroup <group>");
        sender.sendMessage("  /permissions user <name> removegroup <group>");
        sender.sendMessage("  /permissions user <name> set <perm> <true|false>");
    }
}
