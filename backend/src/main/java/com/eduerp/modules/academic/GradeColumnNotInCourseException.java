package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #11: cột điểm không nằm trong cấu hình của khóa thuộc lượt ghi danh đang nhập - ghi
 * vào đây là tạo ra một điểm không bao giờ được tính vào điểm tổng kết của ai, nên chặn thẳng thay vì
 * lưu im lặng. Bao luôn trường hợp id không tồn tại: {@code RecordGrade} chỉ tra trong danh sách cột
 * của đúng khóa đó nên hai tình huống không phân biệt được, và thông báo nói ra cả hai id là đủ.
 */
public final class GradeColumnNotInCourseException extends AcademicException {

    public GradeColumnNotInCourseException(UUID courseGradeColumnId, UUID courseId) {
        super("ACADEMIC_GRADE_COLUMN_NOT_IN_COURSE", HttpStatus.CONFLICT,
                "Cột điểm " + courseGradeColumnId + " không thuộc khóa học " + courseId);
    }
}
