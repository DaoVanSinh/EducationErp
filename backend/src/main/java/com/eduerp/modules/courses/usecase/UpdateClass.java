package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.ClassNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.internal.TeacherAssignmentGuard;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateClass {

    private final ClassRepository classes;
    private final TeacherAssignmentGuard teacherGuard;
    private final ApplicationEventPublisher events;

    UpdateClass(ClassRepository classes, TeacherAssignmentGuard teacherGuard, ApplicationEventPublisher events) {
        this.classes = classes;
        this.teacherGuard = teacherGuard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId, UpdateClassRequest request) {
        var existing = classes.findById(classId).orElseThrow(() -> new ClassNotFoundException(classId));
        teacherGuard.requireTeacher(request.teacherId());

        existing.setTeacherId(request.teacherId());
        existing.setMaxSeats(request.maxSeats());
        existing.setActive(request.active());
        existing.clearSchedule();
        request.schedule()
                .forEach(slot -> existing.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));

        events.publishEvent(new CoursesEvents.ClassUpdated(classId, actorAccountId, actorBranchId));
    }
}
