package com.eduerp.modules.enrollment.internal.roster;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentBranchRosterPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cài đặt {@link StudentBranchRosterPort} của students - enrollment là chủ sở hữu bảng ghi danh. */
@Component
class EnrollmentStudentBranchRoster implements StudentBranchRosterPort {

    private final EnrollmentRepository enrollments;

    EnrollmentStudentBranchRoster(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Override
    public List<UUID> activeStudentProfileIdsAt(UUID branchId) {
        return enrollments.findStudentProfileIdsAtBranch(branchId, EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }
}
