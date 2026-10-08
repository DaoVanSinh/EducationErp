package com.eduerp.modules.academic.web;

import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.academic.usecase.GetClassGradebook;
import com.eduerp.modules.academic.usecase.GetClassProgress;
import com.eduerp.modules.academic.usecase.RecordGrade;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sổ điểm và tiến trình học tập theo lớp. Ba {@code @PreAuthorize} ở đây KHÔNG đòi scope
 * {@code ORGANIZATION} (khác mọi controller khác trong dự án) vì giáo viên chỉ có
 * {@code GRADE:*:PERSONAL} - xem javadoc {@code AccessConstants.AccessRules.READ_GRADE}. Việc kiểm
 * "lớp này có phải lớp của anh không" nằm trong từng usecase qua
 * {@code ClassGradeAccessGuard}, nên không có endpoint nào ở đây bỏ qua được bước đó.
 */
@RestController
@RequestMapping("/api/academic")
class GradeAdminController {

    private final RecordGrade recordGrade;
    private final GetClassGradebook getClassGradebook;
    private final GetClassProgress getClassProgress;

    GradeAdminController(RecordGrade recordGrade, GetClassGradebook getClassGradebook,
            GetClassProgress getClassProgress) {
        this.recordGrade = recordGrade;
        this.getClassGradebook = getClassGradebook;
        this.getClassProgress = getClassProgress;
    }

    /** POST (không PUT): upsert idempotent theo {@code (enrollmentId, courseGradeColumnId)}, và
     * {@code apiClient} ở frontend không phơi method PUT nào. */
    @PostMapping("/grades")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_GRADE)
    void record(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody RecordGradeRequest request) {
        recordGrade.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/classes/{classId}/gradebook")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    GradebookResponse gradebook(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassGradebook.execute(principal.accountId(), classId);
    }

    @GetMapping("/classes/{classId}/progress")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    ClassProgressResponse progress(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassProgress.execute(principal.accountId(), classId);
    }
}
