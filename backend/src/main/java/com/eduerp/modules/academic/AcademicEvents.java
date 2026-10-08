package com.eduerp.modules.academic;

import java.util.UUID;

/** Sự kiện nghiệp vụ của module academic. {@code modules.audit} lắng nghe, academic không biết audit
 * tồn tại (spec mục 9). */
public final class AcademicEvents {

    private AcademicEvents() {
    }

    /** Nhập hoặc sửa điểm một cột của một lượt ghi danh (spec mục 9). */
    public record GradeRecorded(UUID enrollmentId, UUID courseGradeColumnId, UUID actorAccountId,
            UUID actorBranchId) {
    }
}
