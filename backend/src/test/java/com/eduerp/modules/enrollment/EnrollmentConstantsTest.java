package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnrollmentConstantsTest {

    @Test
    void enrollmentStatusHasExactlyThreeValuesInLifecycleOrder() {
        assertThat(EnrollmentConstants.EnrollmentStatus.values()).containsExactly(
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN,
                EnrollmentConstants.EnrollmentStatus.COMPLETED);
    }

    /** Partial unique index trong V17 so sánh literal 'ACTIVE' - tên enum phải khớp từng chữ. */
    @Test
    void activeStatusNameMatchesTheDatabaseIndexPredicate() {
        assertThat(EnrollmentConstants.EnrollmentStatus.ACTIVE.name()).isEqualTo("ACTIVE");
    }
}
