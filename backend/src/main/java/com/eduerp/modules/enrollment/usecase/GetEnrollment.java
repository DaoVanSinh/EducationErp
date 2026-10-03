package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEnrollment {

    private final EnrollmentRepository enrollments;

    GetEnrollment(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse execute(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).map(CreateEnrollment::toResponse)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }
}
