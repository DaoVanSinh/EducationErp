package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EnrollmentExceptionTest {

    @Test
    void enrollmentNotFoundIsNotFound() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void studentProfileNotFoundIsBadRequest() {
        var ex = new StudentProfileNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_STUDENT_PROFILE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void studentNotActiveIsConflict() {
        var ex = new StudentNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_STUDENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void classNotFoundIsBadRequest() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void classNotActiveIsConflict() {
        var ex = new ClassNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void classFullIsConflictAndNamesTheSeatCount() {
        var classId = UUID.randomUUID();
        var ex = new ClassFullException(classId, 12);
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_FULL");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("12");
    }

    @Test
    void duplicateActiveEnrollmentIsConflict() {
        var ex = new DuplicateActiveEnrollmentException(UUID.randomUUID(), UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_DUPLICATE_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void scheduleConflictIsConflictAndCarriesTheDescription() {
        var ex = ScheduleConflictException.acceptable("Trùng lịch với lớp HE-TOAN");
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_SCHEDULE_CONFLICT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("HE-TOAN").doesNotContain("liên hệ quản trị viên");
    }

    @Test
    void scheduleConflictForBranchStaffTellsThemToContactTheAdmin() {
        var ex = ScheduleConflictException.requiresAdmin("Trùng lịch với lớp HE-TOAN.");
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_SCHEDULE_CONFLICT_CONTACT_ADMIN");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("HE-TOAN", "liên hệ quản trị viên để được tư vấn đổi lịch");
    }

    @Test
    void enrollmentNotActiveIsConflict() {
        var ex = new EnrollmentNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
