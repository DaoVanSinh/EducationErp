package com.eduerp.modules.teachers;

import java.util.UUID;

public final class TeachersEvents {

    private TeachersEvents() {
    }

    public record TeacherProfileCreated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record TeacherProfileUpdated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    /** Cho nghỉ: lương tháng dừng từ ngày nghỉ. */
    public record TeacherLeft(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    /** Đổi mức lương là thao tác nhạy cảm: luôn để lại dấu vết trong nhật ký. */
    public record TeacherPayUpdated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }
}
