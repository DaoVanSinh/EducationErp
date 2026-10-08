package com.eduerp.modules.identity.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void lowercasesAndTrims() {
        assertThat(EmailNormalizer.normalize("  Admin@EduERP.Local \t")).isEqualTo("admin@eduerp.local");
    }

    @Test
    void leavesAnAlreadyNormalizedEmailUntouched() {
        assertThat(EmailNormalizer.normalize("admin@eduerp.local")).isEqualTo("admin@eduerp.local");
    }

    @Test
    void keepsDotsAndPlusTagsBecauseTheyAreProviderPolicyNotEmailStandard() {
        assertThat(EmailNormalizer.normalize("First.Last+qldt@Eduerp.local"))
                .isEqualTo("first.last+qldt@eduerp.local");
    }

    @Test
    void passesNullThroughSoBeanValidationReportsItInsteadOfThisClass() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
