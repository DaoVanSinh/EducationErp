package com.eduerp.modules.courses;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Xoá một cột điểm đã có ít nhất một điểm nhập sẽ vi phạm khoá ngoại ở mức DB:
 * {@code grade_entries.course_grade_column_id} (V24, module {@code academic}) tham chiếu
 * {@code course_grade_columns}, nhưng {@code modules.courses} không được biết tới bảng đó tồn tại
 * (rule #3) nên không tự kiểm trước được - lỗi chỉ lộ ra dưới dạng
 * {@code DataIntegrityViolationException} lúc flush (Final review Critical #1). Dịch sang đây để
 * người dùng thấy 409 có errorCode thay vì một 500 trống.
 */
public final class CourseGradeColumnInUseException extends CoursesException {

    public CourseGradeColumnInUseException(UUID courseId) {
        super("COURSES_GRADE_COLUMN_IN_USE", HttpStatus.CONFLICT,
                "Không thể xoá một hoặc nhiều cột điểm của khóa học " + courseId
                        + " vì đã có điểm được nhập cho cột đó");
    }
}
