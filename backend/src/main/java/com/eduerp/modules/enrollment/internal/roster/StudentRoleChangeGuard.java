package com.eduerp.modules.enrollment.internal.roster;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.RoleAssignmentParticipant;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.StudentHasActiveEnrollmentsException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chặn đổi vai trò làm MẤT năng lực học viên khi người đó còn đang học - nếu không, học viên mất cổng học
 * tập giữa khoá trong khi lớp, điểm danh, hoá đơn vẫn coi họ là "đang học".
 */
@Component
class StudentRoleChangeGuard implements RoleAssignmentParticipant {

    private final EnrollmentRepository enrollments;
    private final StudentsManagement students;

    StudentRoleChangeGuard(EnrollmentRepository enrollments, StudentsManagement students) {
        this.enrollments = enrollments;
        this.students = students;
    }

    @Override
    public void beforeRoleChange(UUID accountId, Set<EffectivePermission> before, Set<EffectivePermission> after) {
        boolean wasLearner = EffectivePermission.grants(before, AccessConstants.Resources.LEARNING,
                AccessConstants.Actions.READ);
        boolean staysLearner = EffectivePermission.grants(after, AccessConstants.Resources.LEARNING,
                AccessConstants.Actions.READ);
        if (!wasLearner || staysLearner) {
            return;
        }
        var stillStudying = students.findProfileByAccountId(accountId)
                .map(profile -> enrollments.existsByStudentProfileIdAndStatus(profile.studentProfileId(),
                        EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .orElse(false);
        if (stillStudying) {
            throw new StudentHasActiveEnrollmentsException(accountId);
        }
    }
}
