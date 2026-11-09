package com.eduerp.modules.courses;

import java.util.Optional;
import java.util.UUID;

/**
 * Courses hỏi teachers xem một người có đủ điều kiện nhận lớp không. Khai báo ở đây để courses không phụ thuộc ngược
 * vào teachers (teachers vốn đã đọc courses) - cùng kỹ thuật với {@link ClassRosterPort}.
 */
public interface TeacherEligibilityPort {

    /** Lý do không nhận lớp được, rỗng nếu đủ điều kiện. */
    Optional<String> assignmentProblem(UUID teacherAccountId);
}
