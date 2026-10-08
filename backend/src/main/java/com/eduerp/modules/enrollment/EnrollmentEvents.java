package com.eduerp.modules.enrollment;

import java.util.UUID;

/** Mirror {@code CoursesEvents}: ba record phẳng, cùng hình dạng (id, actor, actorBranch). */
public final class EnrollmentEvents {

    private EnrollmentEvents() {
    }

    public record EnrollmentCreated(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record EnrollmentWithdrawn(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record EnrollmentCompleted(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }
}
