package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rút khỏi lớp giữ nguyên mọi hoá đơn đã phát hành (spec mục 12) - ở đây không có bất kỳ lệnh gọi
 * nào sang modules.billing, và cũng không được phép có (enrollment không biết billing tồn tại). */
@Service
public class WithdrawEnrollment {

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;
    private final AccessManagement access;

    WithdrawEnrollment(EnrollmentRepository enrollments, ApplicationEventPublisher events, AccessManagement access) {
        this.enrollments = enrollments;
        this.events = events;
        this.access = access;
    }

    @Transactional
    public void execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.ENROLLMENT, AccessConstants.Actions.UPDATE)
                .requireCovers(enrollment.getBranchId());
        if (enrollment.getStatus() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveException(enrollmentId);
        }
        enrollment.withdraw();
        events.publishEvent(new EnrollmentEvents.EnrollmentWithdrawn(enrollmentId, actorAccountId, actorBranchId));
    }
}
