package com.eduerp.modules.courses.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.courses.ClassRosterPort;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.internal.CenterClock;
import com.eduerp.modules.courses.internal.ClassAccessGuard;
import com.eduerp.modules.courses.internal.TeacherAssignmentGuard;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.rules.ClassLifecycleRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sửa lớp còn mở (đang tuyển sinh/đang học). Ngày khai giảng và hai mức sĩ số là điều kiện tuyển sinh, nên chỉ
 * đổi được khi lớp chưa khai giảng: lớp đã học không nhận thêm ai, đổi sĩ số không còn ý nghĩa gì.
 */
@Service
public class UpdateClass {

    private final ClassAccessGuard classGuard;
    private final TeacherAssignmentGuard teacherGuard;
    private final ClassRosterPort roster;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    UpdateClass(ClassAccessGuard classGuard, TeacherAssignmentGuard teacherGuard, ClassRosterPort roster,
            CenterClock clock, ApplicationEventPublisher events) {
        this.classGuard = classGuard;
        this.teacherGuard = teacherGuard;
        this.roster = roster;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId, UpdateClassRequest request) {
        var existing = classGuard.loadForUpdate(actorAccountId, classId);
        ClassLifecycleRules.requireEditable(classId, existing.getStatus());
        if (!existing.getTeacherId().equals(request.teacherId())) {
            teacherGuard.requireTeacher(request.teacherId());
        }
        var enrolling = existing.getStatus() == CoursesConstants.ClassStatus.ENROLLING;
        if (!enrolling) {
            requireEnrollmentTermsUnchanged(existing, request);
        }
        ClassLifecycleRules.requireValidPlan(request.minSeats(), request.maxSeats(), request.startDate(),
                request.endDate());
        if (enrolling) {
            applyEnrollmentTerms(classId, existing, request);
        }

        existing.setTeacherId(request.teacherId());
        existing.setEndDate(request.endDate());
        existing.clearSchedule();
        request.schedule()
                .forEach(slot -> existing.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));

        events.publishEvent(new CoursesEvents.ClassUpdated(classId, actorAccountId, actorBranchId));
    }

    /** Kiểm tra trước luật min/max để lớp đã khai giảng nhận đúng lý do, không phải lỗi của ô đang bị khoá. */
    private static void requireEnrollmentTermsUnchanged(Class existing, UpdateClassRequest request) {
        if (!existing.getStartDate().equals(request.startDate())) {
            throw new AppValidationException("startDate", "Lớp đã khai giảng, không đổi được ngày khai giảng");
        }
        if (existing.getMaxSeats() != request.maxSeats()) {
            throw new AppValidationException("maxSeats", "Lớp đã khai giảng, không đổi được sĩ số tối đa");
        }
        if (existing.getMinSeats() != request.minSeats()) {
            throw new AppValidationException("minSeats", "Lớp đã khai giảng, không đổi được sĩ số tối thiểu");
        }
    }

    private void applyEnrollmentTerms(UUID classId, Class existing, UpdateClassRequest request) {
        ClassLifecycleRules.requireSeatsCoverEnrolled(request.maxSeats(), roster.countActive(classId));
        if (!existing.getStartDate().equals(request.startDate())) {
            ClassLifecycleRules.requireStartDateNotPast(request.startDate(), clock.today());
        }
        existing.setStartDate(request.startDate());
        existing.setMinSeats(request.minSeats());
        existing.setMaxSeats(request.maxSeats());
    }
}
