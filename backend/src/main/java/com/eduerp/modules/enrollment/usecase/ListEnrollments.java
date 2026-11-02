package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.EnrollmentResponseAssembler;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Các filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào. Chi nhánh không phải
 * filter của client: scope BRANCH bị ép về chi nhánh chủ quản (spec 6.4, lớp 2), ORGANIZATION không lọc.
 */
@Service
public class ListEnrollments {

    private final EnrollmentRepository enrollments;
    private final AccessManagement access;
    private final EnrollmentResponseAssembler assembler;

    ListEnrollments(EnrollmentRepository enrollments, AccessManagement access, EnrollmentResponseAssembler assembler) {
        this.enrollments = enrollments;
        this.access = access;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> execute(UUID actorAccountId, Pageable pageable, UUID studentProfileId,
            UUID classId, EnrollmentConstants.EnrollmentStatus status) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.ENROLLMENT,
                AccessConstants.Actions.READ).branchFilter(null);
        var page = enrollments.search(studentProfileId, classId, status, branchId, pageable);
        return PageResponse.of(page, assembler.toResponses(page.getContent()));
    }
}
