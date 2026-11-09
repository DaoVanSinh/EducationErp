package com.eduerp.modules.courses;

import java.time.LocalDate;
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

    /** Riêng với {@link ClassUpdated}: sửa phòng hay lịch không được làm đổi hạn đóng học phí. */
    public record ClassStartDateChanged(UUID classId, LocalDate oldStartDate, LocalDate newStartDate) {
    }

    /** {@code actorAccountId} null = tác vụ tự khai giảng của hệ thống (mirror {@code InvoiceOverdue}). */
    public record ClassStarted(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCompleted(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCancelled(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
