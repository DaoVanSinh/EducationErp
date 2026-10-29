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

    /** {@code actorAccountId} null = tác vụ tự khai giảng của hệ thống (mirror {@code InvoiceOverdue}). */
    public record ClassStarted(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCompleted(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCancelled(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
