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
 * Kết thúc lớp đang học: chốt ngày kết thúc thật, mọi ghi danh đang học chuyển sang hoàn tất trong cùng
 * transaction (qua {@link ClassRosterPort}) - không có lúc lớp đã kết thúc mà học viên vẫn "đang học".
 */
@Service
public class CompleteClass {

    private final ClassAccessGuard classGuard;
    private final ClassRosterPort roster;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    CompleteClass(ClassAccessGuard classGuard, ClassRosterPort roster, CenterClock clock,
            ApplicationEventPublisher events) {
        this.classGuard = classGuard;
        this.roster = roster;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId) {
        var cls = classGuard.loadForUpdate(actorAccountId, classId);
        ClassLifecycleRules.requireCompletable(classId, cls.getStatus());
        cls.complete(clock.now(), clock.today());
        roster.completeActiveEnrollments(classId, actorAccountId, actorBranchId);
        events.publishEvent(new CoursesEvents.ClassCompleted(classId, actorAccountId, actorBranchId));
    }
}
