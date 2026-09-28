package com.eduerp.modules.identity.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.IdentityConstants;
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
}
