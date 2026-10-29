package com.eduerp.modules.assessment.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.dto.AssignmentResponses;
import com.eduerp.modules.assessment.dto.CreatedResponse;
import com.eduerp.modules.assessment.usecase.CreateAssignment;
import com.eduerp.modules.assessment.usecase.GetClassAssignments;
import com.eduerp.modules.assessment.usecase.RecordAssignmentResults;
import com.eduerp.modules.assessment.usecase.UpdateAssignment;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bài tập / mini-test theo lớp. Không đòi scope ORGANIZATION ở {@code @PreAuthorize} (giáo viên chỉ có
 * PERSONAL); kiểm "lớp của anh" nằm trong mọi usecase qua {@code ClassAssessmentAccessGuard}.
 */
@RestController
@RequestMapping("/api/assessment")
class AssignmentController {

    private final CreateAssignment createAssignment;
    private final UpdateAssignment updateAssignment;
    private final RecordAssignmentResults recordAssignmentResults;
    private final GetClassAssignments getClassAssignments;

    AssignmentController(CreateAssignment createAssignment, UpdateAssignment updateAssignment,
            RecordAssignmentResults recordAssignmentResults, GetClassAssignments getClassAssignments) {
        this.createAssignment = createAssignment;
        this.updateAssignment = updateAssignment;
        this.recordAssignmentResults = recordAssignmentResults;
        this.getClassAssignments = getClassAssignments;
    }

    @PostMapping("/assignments")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ASSIGNMENT)
    CreatedResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AssignmentRequests.CreateAssignmentRequest request) {
        return createAssignment.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/assignments/{assignmentId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ASSIGNMENT)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentRequests.UpdateAssignmentRequest request) {
        updateAssignment.execute(principal.accountId(), assignmentId, request);
    }

    /** POST upsert kết quả nhiều học viên một lần (tình trạng nộp, điểm, nhận xét). */
    @PostMapping("/assignments/{assignmentId}/results")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ASSIGNMENT)
    void recordResults(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentRequests.RecordResultsRequest request) {
        recordAssignmentResults.execute(principal.accountId(), principal.homeBranchId(), assignmentId, request);
    }

    @GetMapping("/classes/{classId}/assignments")
    @PreAuthorize(AccessConstants.AccessRules.READ_ASSIGNMENT)
    AssignmentResponses.ClassAssignmentsResponse classAssignments(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassAssignments.execute(principal.accountId(), classId);
    }
}
