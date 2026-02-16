package net.alloymc.core.permission;

import java.util.List;
import java.util.Map;

/**
 * Per-user permission data including group membership and permission overrides.
 *
 * @param groups      the group names this user belongs to
 * @param permissions per-node overrides (true = grant, false = deny)
 * @param op          whether this user is an operator
 */
public record PermissionUser(
        List<String> groups,
        Map<String, Boolean> permissions,
        boolean op
) {
    public PermissionUser {
        groups = List.copyOf(groups);
        permissions = Map.copyOf(permissions);
    }
}
