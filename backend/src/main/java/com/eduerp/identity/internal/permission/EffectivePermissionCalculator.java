package com.eduerp.identity.internal.permission;

import com.eduerp.identity.EffectivePermission;
import com.eduerp.identity.internal.model.Group;
import com.eduerp.identity.internal.model.Role;
import com.eduerp.identity.internal.rules.IdentityRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Quyền hiệu lực = hợp của quyền từ role và từ các group, mỗi resource+action giữ scope rộng nhất. */
@Component
public class EffectivePermissionCalculator {

    private final IdentityRules rules;

    public EffectivePermissionCalculator(IdentityRules rules) {
        this.rules = rules;
    }

    public Set<EffectivePermission> calculate(Role role, Set<Group> groups) {
        Map<String, EffectivePermission> byKey = new HashMap<>();

        Stream.concat(
                role.getPermissionGroups().stream(),
                groups.stream().flatMap(g -> g.getPermissionGroups().stream()))
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
