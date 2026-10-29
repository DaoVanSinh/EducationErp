package com.eduerp.modules.courses.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.ClassCodeAlreadyExistsException;
import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.internal.CenterClock;
import com.eduerp.modules.courses.internal.TeacherAssignmentGuard;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.courses.internal.rules.ClassLifecycleRules;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mở lớp ở trạng thái Đang tuyển sinh - lớp chỉ thật sự bắt đầu học khi được khai giảng ({@link StartClass}). */
@Service
public class CreateClass {

    private final ClassRepository classes;
    private final CourseRepository courses;
    private final OrganizationManagement organization;
    private final TeacherAssignmentGuard teacherGuard;
    private final AccessManagement access;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    CreateClass(ClassRepository classes, CourseRepository courses, OrganizationManagement organization,
            TeacherAssignmentGuard teacherGuard, AccessManagement access, CenterClock clock,
            ApplicationEventPublisher events) {
        this.classes = classes;
        this.courses = courses;
        this.organization = organization;
        this.teacherGuard = teacherGuard;
        this.access = access;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateClassRequest request) {
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.CLASS, AccessConstants.Actions.CREATE)
                .requireCovers(request.branchId());
        if (classes.findByCode(request.code()).isPresent()) {
            throw new ClassCodeAlreadyExistsException(request.code());
        }
        var course = courses.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        if (!organization.exists(request.branchId())) {
            throw new AppValidationException("branchId", "Chi nhánh không tồn tại");
        }
        teacherGuard.requireTeacher(request.teacherId());
        ClassLifecycleRules.requireValidPlan(request.minSeats(), request.maxSeats(), request.startDate(),
                request.endDate());
        ClassLifecycleRules.requireStartDateNotPast(request.startDate(), clock.today());

        var newClass = new Class(course, request.code(), request.branchId(), request.teacherId(),
                request.maxSeats(), request.minSeats(), request.startDate(), request.endDate());
        request.schedule()
                .forEach(slot -> newClass.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));
        var saved = classes.save(newClass);
        events.publishEvent(new CoursesEvents.ClassCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
