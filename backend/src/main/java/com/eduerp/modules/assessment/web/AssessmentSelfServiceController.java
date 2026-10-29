package com.eduerp.modules.assessment.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.dto.MyAssessmentResponses;
import com.eduerp.modules.assessment.usecase.GetMyAssessments;
import com.eduerp.modules.assessment.usecase.GetMyMiniTask;
import com.eduerp.modules.assessment.usecase.SubmitMiniTaskAttempt;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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
 * Cổng học tập: bài tập và mini-task của CHÍNH học viên. Mọi dữ liệu đi từ {@code principal.accountId()};
 * id mini-task trên URL chỉ được dùng sau khi xác nhận học viên học đúng lớp của mini-task đó.
 */
@RestController
@RequestMapping("/api/assessment/me")
class AssessmentSelfServiceController {

    private final GetMyAssessments getMyAssessments;
    private final GetMyMiniTask getMyMiniTask;
    private final SubmitMiniTaskAttempt submitMiniTaskAttempt;

    AssessmentSelfServiceController(GetMyAssessments getMyAssessments, GetMyMiniTask getMyMiniTask,
            SubmitMiniTaskAttempt submitMiniTaskAttempt) {
        this.getMyAssessments = getMyAssessments;
        this.getMyMiniTask = getMyMiniTask;
        this.submitMiniTaskAttempt = submitMiniTaskAttempt;
    }

    @GetMapping("/classes")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    List<MyAssessmentResponses.MyAssessmentClassResponse> myAssessments(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return getMyAssessments.execute(principal.accountId());
    }

    @GetMapping("/mini-tasks/{miniTaskId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    MiniTaskResponses.PracticeMiniTaskResponse miniTask(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID miniTaskId) {
        return getMyMiniTask.execute(principal.accountId(), miniTaskId);
    }

    @PostMapping("/mini-tasks/{miniTaskId}/attempts")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PRACTICE)
    MiniTaskResponses.AttemptResultResponse submit(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID miniTaskId, @Valid @RequestBody MiniTaskRequests.SubmitAttemptRequest request) {
        return submitMiniTaskAttempt.execute(principal.accountId(), miniTaskId, request);
    }
}
