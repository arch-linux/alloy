package net.alloymc.core.permission;

import java.util.List;

/**
 * A permission group with a set of permission nodes and parent group inheritance.
 *
 * @param permissions the permission nodes directly assigned to this group
 * @param parents     the names of parent groups to inherit permissions from
 */
public record PermissionGroup(
        List<String> permissions,
        List<String> parents
) {
    public PermissionGroup {
        permissions = List.copyOf(permissions);
        parents = List.copyOf(parents);
    }
}
