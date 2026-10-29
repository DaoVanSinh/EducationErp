package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.ClassRosterPort;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.internal.CenterClock;
import com.eduerp.modules.courses.internal.ClassAccessGuard;
import com.eduerp.modules.courses.internal.rules.ClassLifecycleRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khai giảng thủ công: lớp đang tuyển sinh, đã tới ngày khai giảng, có lịch học và đủ sĩ số tối thiểu
 * chuyển sang Đang học. Cùng điều kiện với tác vụ tự khai giảng ({@link AutoStartClasses}) - thủ công chỉ
 * để admin không phải đợi tới nửa đêm.
 */
@Service
public class StartClass {

    private final ClassAccessGuard classGuard;
    private final ClassRosterPort roster;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    StartClass(ClassAccessGuard classGuard, ClassRosterPort roster, CenterClock clock,
            ApplicationEventPublisher events) {
        this.classGuard = classGuard;
        this.roster = roster;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId) {
        var cls = classGuard.loadForUpdate(actorAccountId, classId);
        ClassLifecycleRules.requireStartable(classId, cls.getStatus(), cls.getStartDate(), clock.today(),
                !cls.getSchedule().isEmpty(), roster.countActive(classId), cls.getMinSeats());
        cls.start(clock.now());
        events.publishEvent(new CoursesEvents.ClassStarted(classId, actorAccountId, actorBranchId));
    }
}
