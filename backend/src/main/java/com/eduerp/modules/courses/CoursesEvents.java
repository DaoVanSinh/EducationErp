package com.eduerp.modules.courses;

import java.util.UUID;

public final class CoursesEvents {

    private CoursesEvents() {
    }

    public record CourseCreated(UUID courseId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record CourseUpdated(UUID courseId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCreated(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassUpdated(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
