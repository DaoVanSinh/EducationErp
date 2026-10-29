package com.eduerp.modules.attendance;

import java.util.UUID;

/** Sự kiện nghiệp vụ của module attendance. {@code modules.audit} lắng nghe, attendance không biết
 * audit tồn tại. */
public final class AttendanceEvents {

    private AttendanceEvents() {
    }

    /** Ghi nhận (tạo mới hoặc sửa) điểm danh một buổi của một lớp. */
    public record AttendanceRecorded(UUID sessionId, UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
