package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ba filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào (complexity 1). */
@Service
public class ListEnrollments {

    private final EnrollmentRepository enrollments;

    ListEnrollments(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> execute(Pageable pageable, UUID studentProfileId, UUID classId,
            EnrollmentConstants.EnrollmentStatus status) {
        return PageResponse.of(enrollments.search(studentProfileId, classId, status, pageable)
                .map(CreateEnrollment::toResponse));
    }
}
