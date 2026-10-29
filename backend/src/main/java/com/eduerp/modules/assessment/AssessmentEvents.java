package com.eduerp.modules.assessment;

import java.util.UUID;

/** Sự kiện nghiệp vụ của module assessment - {@code modules.audit} lắng nghe. Lượt làm mini-task của học
 * viên KHÔNG phát sự kiện: đó là ôn luyện, ghi nhật ký từng lượt chỉ làm phình bảng audit. */
public final class AssessmentEvents {

    private AssessmentEvents() {
    }

    public record AssignmentCreated(UUID assignmentId, UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record AssignmentResultsRecorded(UUID assignmentId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record MiniTaskCreated(UUID miniTaskId, UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
