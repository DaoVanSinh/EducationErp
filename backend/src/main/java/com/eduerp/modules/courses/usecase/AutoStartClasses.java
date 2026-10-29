package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.ClassRosterPort;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.internal.CenterClock;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.rules.ClassLifecycleRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tự khai giảng mỗi ngày (trả lời câu "khi nào lớp bắt đầu học"): lớp đang tuyển sinh đã tới ngày khai
 * giảng, có lịch học và đủ sĩ số tối thiểu thì chuyển sang Đang học. Lớp chưa đủ người được GIỮ NGUYÊN ở
 * Đang tuyển sinh - màn Lớp học đánh dấu "quá ngày khai giảng, chưa đủ sĩ số" để admin quyết dời ngày hay huỷ;
 * hệ thống không tự huỷ một lớp đã có người đóng tiền.
 *
 * <p>Idempotent tự nhiên: lớp đã khai giảng không còn nằm trong tập quét. Không có actor -
 * {@code ClassStarted.actorAccountId} là {@code null} (mirror {@code MarkOverdueInvoices}).
 */
@Service
public class AutoStartClasses {

    private final ClassRepository classes;
    private final ClassRosterPort roster;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    AutoStartClasses(ClassRepository classes, ClassRosterPort roster, CenterClock clock,
            ApplicationEventPublisher events) {
        this.classes = classes;
        this.roster = roster;
        this.clock = clock;
        this.events = events;
    }

    /** @return số lớp vừa được khai giảng - để scheduler log một con số thật. */
    @Transactional
    public int execute() {
        var today = clock.today();
        List<Class> candidates = classes.findAllByStatusAndStartDateLessThanEqual(
                CoursesConstants.ClassStatus.ENROLLING, today);
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<UUID, Long> enrolled = roster.countActiveByClass(candidates.stream().map(Class::getId).toList());
        var started = candidates.stream()
                .filter(cls -> ClassLifecycleRules.isStartable(cls.getStatus(), cls.getStartDate(), today,
                        !cls.getSchedule().isEmpty(), enrolled.getOrDefault(cls.getId(), 0L), cls.getMinSeats()))
                .toList();
        started.forEach(cls -> {
            cls.start(clock.now());
            events.publishEvent(new CoursesEvents.ClassStarted(cls.getId(), null, cls.getBranchId()));
        });
        return started.size();
    }
}
