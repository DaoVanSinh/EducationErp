package com.eduerp.modules.enrollment.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.usecase.CompleteEnrollment;
import com.eduerp.modules.enrollment.usecase.CreateEnrollment;
import com.eduerp.modules.enrollment.usecase.GetEnrollment;
import com.eduerp.modules.enrollment.usecase.ListEnrollments;
import com.eduerp.modules.enrollment.usecase.WithdrawEnrollment;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị ghi danh - mirror CourseAdminController 1:1 về cấu trúc. Không có endpoint xoá: ghi danh
 * chỉ chuyển state sang WITHDRAWN/COMPLETED. */
@RestController
@RequestMapping("/api/enrollment/enrollments")
class EnrollmentAdminController {

    private final ListEnrollments listEnrollments;
    private final CreateEnrollment createEnrollment;
    private final GetEnrollment getEnrollment;
    private final WithdrawEnrollment withdrawEnrollment;
    private final CompleteEnrollment completeEnrollment;

    EnrollmentAdminController(ListEnrollments listEnrollments, CreateEnrollment createEnrollment,
            GetEnrollment getEnrollment, WithdrawEnrollment withdrawEnrollment,
            CompleteEnrollment completeEnrollment) {
        this.listEnrollments = listEnrollments;
        this.createEnrollment = createEnrollment;
        this.getEnrollment = getEnrollment;
        this.withdrawEnrollment = withdrawEnrollment;
        this.completeEnrollment = completeEnrollment;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_ENROLLMENT)
    PageResponse<EnrollmentResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID classId) {
        return listEnrollments.execute(pageable, studentProfileId, classId);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ENROLLMENT)
    EnrollmentResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateEnrollmentRequest request) {
        return createEnrollment.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{enrollmentId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_ENROLLMENT)
    EnrollmentResponse get(@PathVariable UUID enrollmentId) {
        return getEnrollment.execute(enrollmentId);
    }

    @PostMapping("/{enrollmentId}/withdraw")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
    void withdraw(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID enrollmentId) {
        withdrawEnrollment.execute(enrollmentId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{enrollmentId}/complete")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
    void complete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID enrollmentId) {
        completeEnrollment.execute(enrollmentId, principal.accountId(), principal.homeBranchId());
    }
}
