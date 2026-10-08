package com.eduerp.modules.academic.dto;

import java.util.List;
import java.util.UUID;

/** {@code standardSessionCount} null = khóa chưa cấu hình tổng số buổi chuẩn, khi đó mọi
 * {@code rows[].progressPercent} cũng null (spec mục 5). */
public record ClassProgressResponse(UUID classId, UUID courseId, Integer standardSessionCount,
        List<StudentProgressRow> rows) {
}
