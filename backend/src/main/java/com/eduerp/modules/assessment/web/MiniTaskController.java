package com.eduerp.modules.assessment.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.dto.CreatedResponse;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.usecase.CreateMiniTask;
import com.eduerp.modules.assessment.usecase.GetClassMiniTasks;
import com.eduerp.modules.assessment.usecase.UpdateMiniTask;
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

/** Soạn và theo dõi mini-task theo lớp - cùng quyền ASSIGNMENT với bài tập / mini-test. */
@RestController
@RequestMapping("/api/assessment")
class MiniTaskController {

    private final CreateMiniTask createMiniTask;
    private final UpdateMiniTask updateMiniTask;
    private final GetClassMiniTasks getClassMiniTasks;

    MiniTaskController(CreateMiniTask createMiniTask, UpdateMiniTask updateMiniTask,
            GetClassMiniTasks getClassMiniTasks) {
        this.createMiniTask = createMiniTask;
        this.updateMiniTask = updateMiniTask;
        this.getClassMiniTasks = getClassMiniTasks;
    }

    @PostMapping("/mini-tasks")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ASSIGNMENT)
    CreatedResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody MiniTaskRequests.CreateMiniTaskRequest request) {
        return createMiniTask.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/mini-tasks/{miniTaskId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ASSIGNMENT)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID miniTaskId,
            @Valid @RequestBody MiniTaskRequests.UpdateMiniTaskRequest request) {
        updateMiniTask.execute(principal.accountId(), miniTaskId, request);
    }

    @GetMapping("/classes/{classId}/mini-tasks")
    @PreAuthorize(AccessConstants.AccessRules.READ_ASSIGNMENT)
    MiniTaskResponses.ClassMiniTasksResponse classMiniTasks(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassMiniTasks.execute(principal.accountId(), classId);
    }
}
