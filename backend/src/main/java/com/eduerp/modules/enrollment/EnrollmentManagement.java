package com.eduerp.modules.enrollment;

import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module enrollment - type DUY NHẤT module khác được phép gọi (rule #1).
 * {@code modules.billing} gọi {@link #getEnrollment(UUID)} để chốt snapshot student/course/branch và
 * để chặn phát hành hoá đơn mới cho ghi danh không còn {@code ACTIVE} (spec mục 5).
 */
@Service
public class EnrollmentManagement {

    public record EnrollmentSummaryResponse(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
            EnrollmentConstants.EnrollmentStatus status) {
    }

    private final EnrollmentRepository enrollments;

    EnrollmentManagement(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentSummaryResponse> getEnrollment(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).map(enrollment -> new EnrollmentSummaryResponse(
                enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getCourseId(),
                enrollment.getBranchId(), enrollment.getStatus()));
    }
}
