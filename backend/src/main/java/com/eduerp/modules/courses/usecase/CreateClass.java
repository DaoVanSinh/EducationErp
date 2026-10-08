package com.eduerp.modules.courses.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.courses.ClassCodeAlreadyExistsException;
import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateClass {

    private final ClassRepository classes;
    private final CourseRepository courses;
    private final OrganizationManagement organization;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    CreateClass(ClassRepository classes, CourseRepository courses, OrganizationManagement organization,
            IdentityManagement identity, ApplicationEventPublisher events) {
        this.classes = classes;
        this.courses = courses;
        this.organization = organization;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateClassRequest request) {
        if (classes.findByCode(request.code()).isPresent()) {
            throw new ClassCodeAlreadyExistsException(request.code());
        }
        var course = courses.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        if (!organization.exists(request.branchId())) {
            throw new AppValidationException("branchId", "Chi nhánh không tồn tại");
        }
        if (!identity.summariesOf(List.of(request.teacherId())).containsKey(request.teacherId())) {
            throw new AppValidationException("teacherId", "Giáo viên không tồn tại");
        }

        var newClass = new Class(course, request.code(), request.branchId(), request.teacherId(),
                request.maxSeats());
        request.schedule()
                .forEach(slot -> newClass.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));
        var saved = classes.save(newClass);
        events.publishEvent(new CoursesEvents.ClassCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
