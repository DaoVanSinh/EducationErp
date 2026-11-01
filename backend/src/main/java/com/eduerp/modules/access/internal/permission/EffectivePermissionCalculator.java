package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.rules.AccessRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Quyền hiệu lực = các quyền trong những nhóm quyền của vai trò, mỗi resource+action giữ scope rộng nhất. */
@Component
public class EffectivePermissionCalculator {

    private final AccessRules rules;

    public EffectivePermissionCalculator(AccessRules rules) {
        this.rules = rules;
    }

    public Set<EffectivePermission> calculate(Role role) {
        Map<String, EffectivePermission> byKey = new HashMap<>();

        role.getPermissionGroups().stream()
                .flatMap(pg -> pg.getItems().stream())
                .forEach(item -> {
                    var candidate = new EffectivePermission(
                            item.getPermission().getResource(),
                            item.getPermission().getAction(),
                            item.getScope());
                    byKey.merge(candidate.key(), candidate, this::broader);
                });

        return new HashSet<>(byKey.values());
    }

    private EffectivePermission broader(EffectivePermission candidate, EffectivePermission current) {
        return rules.isBroaderOrEqual(candidate.scope(), current.scope()) ? candidate : current;
    }
}
