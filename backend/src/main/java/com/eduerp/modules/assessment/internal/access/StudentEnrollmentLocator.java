package com.eduerp.modules.assessment.internal.access;

import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Câu hỏi của phía học viên: "tài khoản đang đăng nhập có học lớp này không, và qua lượt ghi danh nào".
 * Đi từ {@code accountId} của phiên - không bao giờ nhận enrollmentId từ client - nên học viên không làm
 * bài hộ hay xem đề của lớp khác được.
 *
 * <p>Ghi danh đã RÚT không được tính; ghi danh đã HOÀN TẤT vẫn được ôn luyện (mini-task là để tự học).
 */
@Component
public class StudentEnrollmentLocator {

    private final StudentsManagement students;
    private final EnrollmentManagement enrollments;

    StudentEnrollmentLocator(StudentsManagement students, EnrollmentManagement enrollments) {
        this.students = students;
        this.enrollments = enrollments;
    }

    /** @throws AssessmentException.NotEnrolledInClass không có hồ sơ, hoặc không có ghi danh hợp lệ (403) */
    public UUID requireEnrollment(UUID accountId, UUID classId) {
        var profile = students.findProfileByAccountId(accountId)
                .orElseThrow(() -> new AssessmentException.NotEnrolledInClass(classId));
        return enrollments.listEnrollmentsByStudent(profile.studentProfileId()).stream()
                .filter(entry -> entry.classId().equals(classId))
                .filter(entry -> entry.status() != EnrollmentConstants.EnrollmentStatus.WITHDRAWN)
                .map(EnrollmentManagement.StudentEnrollmentEntry::enrollmentId)
                .findFirst()
                .orElseThrow(() -> new AssessmentException.NotEnrolledInClass(classId));
    }
}
