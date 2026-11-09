package com.eduerp.modules.enrollment.internal.roster;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.StudentHasActiveEnrollmentsException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Còn đang học thì chưa đóng tài khoản được: phải rút khỏi lớp hoặc đợi lớp kết thúc trước. */
@Component
class StudentEnrollmentLifecycle implements AccountLifecycleParticipant {

    private final EnrollmentRepository enrollments;
    private final StudentsManagement students;

    StudentEnrollmentLifecycle(EnrollmentRepository enrollments, StudentsManagement students) {
        this.enrollments = enrollments;
        this.students = students;
    }

    @Override
    public void beforeClose(UUID accountId) {
        var stillStudying = students.findProfileByAccountId(accountId)
                .map(profile -> enrollments.existsByStudentProfileIdAndStatus(profile.studentProfileId(),
                        EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .orElse(false);
        if (stillStudying) {
            throw new StudentHasActiveEnrollmentsException(accountId);
        }
    }
}
