package com.eduerp.identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class EffectivePermissionCalculator {

    Set<EffectivePermission> calculate(Role role, Set<Group> groups) {
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

    private EffectivePermission broader(EffectivePermission a, EffectivePermission b) {
        return a.scope().rank() >= b.scope().rank() ? a : b;
    }
}
