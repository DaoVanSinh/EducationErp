package com.eduerp.modules.access.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import org.junit.jupiter.api.Test;

class AccessRulesTest {

    private final AccessRules rules = new AccessRules();

    @Test
    void broaderScopeWins() {
        assertThat(rules.isBroaderOrEqual(AccessConstants.PermissionScope.ORGANIZATION,
                AccessConstants.PermissionScope.BRANCH)).isTrue();
        assertThat(rules.isBroaderOrEqual(AccessConstants.PermissionScope.PERSONAL,
                AccessConstants.PermissionScope.BRANCH)).isFalse();
    }
}
