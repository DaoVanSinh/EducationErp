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
 * Huỷ lớp chưa khai giảng (thường vì không đủ học viên): mọi ghi danh đang học chuyển sang đã rút trong
 * cùng transaction. Hoá đơn đã phát hành KHÔNG tự huỷ - đó là chứng từ tài chính, kế toán xử lý hoàn/huỷ
 * riêng ở màn Hoá đơn (mirror luật "rút khỏi lớp không huỷ hoá đơn" của Phân hệ 3).
 */
@Service
public class CancelClass {

    private final ClassAccessGuard classGuard;
    private final ClassRosterPort roster;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    CancelClass(ClassAccessGuard classGuard, ClassRosterPort roster, CenterClock clock,
            ApplicationEventPublisher events) {
        this.classGuard = classGuard;
        this.roster = roster;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId) {
        var cls = classGuard.loadForUpdate(actorAccountId, classId);
        ClassLifecycleRules.requireCancellable(classId, cls.getStatus());
        cls.cancel(clock.now());
        roster.withdrawActiveEnrollments(classId, actorAccountId, actorBranchId);
        events.publishEvent(new CoursesEvents.ClassCancelled(classId, actorAccountId, actorBranchId));
    }
}
