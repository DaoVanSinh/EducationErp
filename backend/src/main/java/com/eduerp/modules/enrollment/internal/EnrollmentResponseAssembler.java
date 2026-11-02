package com.eduerp.modules.enrollment.internal;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.students.StudentsManagement;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Một chỗ duy nhất biến {@link Enrollment} thành {@link EnrollmentResponse}. Tên học viên và tên lớp thuộc
 * hai module khác nên không JOIN được: gom id của cả danh sách, tra một lần qua facade rồi ghép ở đây.
 */
@Component
public class EnrollmentResponseAssembler {

    private final StudentsManagement students;
    private final CoursesManagement courses;

    public EnrollmentResponseAssembler(StudentsManagement students, CoursesManagement courses) {
        this.students = students;
        this.courses = courses;
    }

    public EnrollmentResponse toResponse(Enrollment enrollment) {
        return toResponses(List.of(enrollment)).get(0);
    }

    public List<EnrollmentResponse> toResponses(List<Enrollment> enrollments) {
        var studentNames = students.displayNamesOf(
                enrollments.stream().map(Enrollment::getStudentProfileId).distinct().toList());
        var classes = courses.classSummariesOf(enrollments.stream().map(Enrollment::getClassId).distinct().toList());
        return enrollments.stream()
                .map(enrollment -> toResponse(enrollment, studentNames.get(enrollment.getStudentProfileId()),
                        classes.get(enrollment.getClassId())))
                .toList();
    }

    private static EnrollmentResponse toResponse(Enrollment enrollment, String studentName,
            CoursesManagement.ClassBasicSummary classSummary) {
        var classCode = classSummary == null ? null : classSummary.code();
        var courseName = classSummary == null ? null : classSummary.courseName();
        return new EnrollmentResponse(enrollment.getId(), enrollment.getStudentProfileId(), studentName,
                enrollment.getClassId(), classCode, enrollment.getCourseId(), courseName, enrollment.getBranchId(),
                enrollment.getStatus(), enrollment.getEnrolledAt(), enrollment.getWithdrawnAt());
    }
}
