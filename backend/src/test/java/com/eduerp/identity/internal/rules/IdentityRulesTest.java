package com.eduerp.identity.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.identity.IdentityConstants;
import org.junit.jupiter.api.Test;

class IdentityRulesTest {

    private final IdentityRules rules = new IdentityRules();

    @Test
    void onlyActiveAccountsMaySignIn() {
        assertThat(rules.canSignIn(IdentityConstants.AccountStatus.ACTIVE)).isTrue();
        assertThat(rules.canSignIn(IdentityConstants.AccountStatus.DISABLED)).isFalse();
    }

    @Test
    void onlyRefreshTokenMayRotateASession() {
        assertThat(rules.isRefreshToken(IdentityConstants.TokenTypes.REFRESH)).isTrue();
        assertThat(rules.isRefreshToken(IdentityConstants.TokenTypes.ACCESS)).isFalse();
        assertThat(rules.isRefreshToken(null)).isFalse();
    }

    @Test
    void broaderScopeWins() {
        assertThat(rules.isBroaderOrEqual(IdentityConstants.PermissionScope.ORGANIZATION,
                IdentityConstants.PermissionScope.BRANCH)).isTrue();
        assertThat(rules.isBroaderOrEqual(IdentityConstants.PermissionScope.PERSONAL,
                IdentityConstants.PermissionScope.BRANCH)).isFalse();
    }
}
