package com.eduerp.modules.identity.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.IdentityConstants;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void newAccountHasNoLastLogin() {
        var account = new Account("a@b.com", "hash", "A", null);
        assertThat(account.getLastLogin()).isNull();
    }

    @Test
    void recordFirstLoginSetsTimestamp() {
        var account = new Account("a@b.com", "hash", "A", null);
        account.recordFirstLogin();
        assertThat(account.getLastLogin()).isNotNull();
    }

    @Test
    void disableThenActivateRestoresActiveStatus() {
        var account = new Account("a@b.com", "hash", "A", null);
        account.disable();
        assertThat(account.getStatus()).isEqualTo(IdentityConstants.AccountStatus.DISABLED);
        account.activate();
        assertThat(account.getStatus()).isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
    }
}
