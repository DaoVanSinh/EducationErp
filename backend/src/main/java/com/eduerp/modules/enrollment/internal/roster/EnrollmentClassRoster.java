package com.eduerp.modules.enrollment.internal.roster;

import com.eduerp.modules.courses.ClassRosterPort;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link ClassRosterPort} của courses - enrollment là chủ sở hữu bảng ghi danh, nên đếm sĩ số và
 * đóng danh sách lớp phải đi qua đây. Mỗi ghi danh bị đóng vẫn phát đúng sự kiện như khi admin bấm tay
 * từng người, để nhật ký thao tác không có lỗ hổng.
 */
@Component
class EnrollmentClassRoster implements ClassRosterPort {

    private static final EnrollmentConstants.EnrollmentStatus ACTIVE = EnrollmentConstants.EnrollmentStatus.ACTIVE;

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;

    EnrollmentClassRoster(EnrollmentRepository enrollments, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.events = events;
    }

    @Override
    public long countActive(UUID classId) {
        return enrollments.countByClassIdAndStatus(classId, ACTIVE);
    }

    @Override
    public Map<UUID, Long> countActiveByClass(Collection<UUID> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return enrollments.countByClassIdsAndStatus(classIds, ACTIVE).stream()
                .collect(Collectors.toMap(EnrollmentRepository.ClassCountRow::getClassId,
                        EnrollmentRepository.ClassCountRow::getTotal));
    }

    @Override
    public void completeActiveEnrollments(UUID classId, UUID actorAccountId, UUID actorBranchId) {
        for (Enrollment enrollment : enrollments.findAllByClassIdAndStatus(classId, ACTIVE)) {
            enrollment.complete();
            events.publishEvent(new EnrollmentEvents.EnrollmentCompleted(enrollment.getId(), actorAccountId,
                    actorBranchId));
        }
    }

    @Override
    public void withdrawActiveEnrollments(UUID classId, UUID actorAccountId, UUID actorBranchId) {
        for (Enrollment enrollment : enrollments.findAllByClassIdAndStatus(classId, ACTIVE)) {
            enrollment.withdraw();
            events.publishEvent(new EnrollmentEvents.EnrollmentWithdrawn(enrollment.getId(), actorAccountId,
                    actorBranchId));
        }
    }
}
