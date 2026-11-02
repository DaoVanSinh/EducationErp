package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.EnrollmentResponseAssembler;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEnrollment {

    private final EnrollmentRepository enrollments;
    private final AccessManagement access;
    private final EnrollmentResponseAssembler assembler;

    GetEnrollment(EnrollmentRepository enrollments, AccessManagement access, EnrollmentResponseAssembler assembler) {
        this.enrollments = enrollments;
        this.access = access;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse execute(UUID actorAccountId, UUID enrollmentId) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.ENROLLMENT, AccessConstants.Actions.READ)
                .requireCovers(enrollment.getBranchId());
        return assembler.toResponse(enrollment);
    }
}
