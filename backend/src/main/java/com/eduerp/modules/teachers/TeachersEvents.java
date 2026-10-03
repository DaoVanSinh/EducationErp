package com.eduerp.modules.teachers;

import java.util.UUID;

public final class TeachersEvents {

    private TeachersEvents() {
    }

    public record TeacherProfileCreated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record TeacherProfileUpdated(UUID teacherProfileId, UUID actorAccountId, UUID actorBranchId) {
    }
}
