package com.eduerp.modules.academic.dto;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.util.List;
import java.util.UUID;

/** {@code columns} theo {@code displayOrder} - cả giao diện và file Excel xuất ra đều lấy thứ tự cột
 * từ đây, không tự sắp lại. */
public record GradebookResponse(UUID classId, UUID courseId, List<GradeColumnResponse> columns,
        List<StudentGradesRow> rows) {
}
