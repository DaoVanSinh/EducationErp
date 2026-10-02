package com.eduerp.modules.courses.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.courses.ClassNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateClass {

    private final ClassRepository classes;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    UpdateClass(ClassRepository classes, IdentityManagement identity, ApplicationEventPublisher events) {
        this.classes = classes;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId, UpdateClassRequest request) {
        var existing = classes.findById(classId).orElseThrow(() -> new ClassNotFoundException(classId));
        if (!identity.summariesOf(List.of(request.teacherId())).containsKey(request.teacherId())) {
            throw new AppValidationException("teacherId", "Giáo viên không tồn tại");
        }

        existing.setTeacherId(request.teacherId());
        existing.setMaxSeats(request.maxSeats());
        existing.setActive(request.active());
        existing.clearSchedule();
        request.schedule()
                .forEach(slot -> existing.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));

        events.publishEvent(new CoursesEvents.ClassUpdated(classId, actorAccountId, actorBranchId));
    }
}
