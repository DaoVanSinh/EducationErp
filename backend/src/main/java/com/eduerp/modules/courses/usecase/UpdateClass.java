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
import com.eduerp.modules.courses.internal.model.ClassTeacherAssignment;
import com.eduerp.modules.courses.internal.repository.ClassTeacherAssignmentRepository;
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
    private final ClassTeacherAssignmentRepository assignments;
    private final CenterClock clock;
    private final ApplicationEventPublisher events;

    UpdateClass(ClassAccessGuard classGuard, TeacherAssignmentGuard teacherGuard, ClassRosterPort roster,
            ClassTeacherAssignmentRepository assignments,
            CenterClock clock, ApplicationEventPublisher events) {
        this.classGuard = classGuard;
        this.teacherGuard = teacherGuard;
        this.roster = roster;
        this.assignments = assignments;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId, UpdateClassRequest request) {
        var existing = classGuard.loadForUpdate(actorAccountId, classId);
        ClassLifecycleRules.requireEditable(classId, existing.getStatus());
        var teacherChanged = !existing.getTeacherId().equals(request.teacherId());
        var enrolling = existing.getStatus() == CoursesConstants.ClassStatus.ENROLLING;
        var oldStartDate = existing.getStartDate();
        if (!enrolling) {
            requireEnrollmentTermsUnchanged(existing, request);
        }
        ClassLifecycleRules.requireValidPlan(request.minSeats(), request.maxSeats(), request.startDate(),
                request.endDate());
        if (teacherChanged) {
            teacherGuard.requireAssignable(request.teacherId(), new TeacherAssignmentGuard.ClassPlan(
                    existing.getBranchId(), request.schedule(), request.startDate(), request.endDate(), classId));
        }
        if (enrolling) {
            applyEnrollmentTerms(classId, existing, request);
        }
        recordTeacherAssignment(classId, existing, request, enrolling, teacherChanged);

        existing.setTeacherId(request.teacherId());
        existing.setEndDate(request.endDate());
        existing.clearSchedule();
        request.schedule()
                .forEach(slot -> existing.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));

        events.publishEvent(new CoursesEvents.ClassUpdated(classId, actorAccountId, actorBranchId));
        if (!oldStartDate.equals(existing.getStartDate())) {
            events.publishEvent(new CoursesEvents.ClassStartDateChanged(classId, oldStartDate,
                    existing.getStartDate()));
        }
    }

    /**
     * Lớp chưa khai giảng chỉ có một dòng: giáo viên hiện tại từ ngày khai giảng. Lớp đã học thì thay giáo viên tạo
     * dòng mới có "từ ngày", để buổi trước ngày đó vẫn tính giờ cho giáo viên cũ kể cả khi ghi bù.
     */
    private void recordTeacherAssignment(UUID classId, Class existing, UpdateClassRequest request, boolean enrolling,
            boolean teacherChanged) {
        var latest = assignments.findFirstByClassIdOrderByEffectiveFromDesc(classId);
        if (enrolling) {
            latest.ifPresentOrElse(row -> row.reassign(request.teacherId(), request.startDate()),
                    () -> assignments.save(new ClassTeacherAssignment(classId, request.teacherId(),
                            request.startDate())));
            return;
        }
        if (!teacherChanged) {
            return;
        }
        var today = clock.today();
        var effectiveFrom = request.teacherEffectiveFrom() == null ? today : request.teacherEffectiveFrom();
        if (effectiveFrom.isAfter(today) || effectiveFrom.isBefore(existing.getStartDate())) {
            throw new AppValidationException("teacherEffectiveFrom",
                    "Ngày thay giáo viên phải từ ngày khai giảng tới hôm nay");
        }
        var previousFrom = latest.map(ClassTeacherAssignment::getEffectiveFrom).orElse(existing.getStartDate());
        if (effectiveFrom.isBefore(previousFrom)) {
            throw new AppValidationException("teacherEffectiveFrom",
                    "Ngày thay giáo viên phải sau lần thay gần nhất (" + previousFrom + ")");
        }
        if (latest.isEmpty()) {
            assignments.save(new ClassTeacherAssignment(classId, existing.getTeacherId(), existing.getStartDate()));
        }
        if (effectiveFrom.equals(previousFrom) && latest.isPresent()) {
            latest.get().reassign(request.teacherId(), effectiveFrom);
            return;
        }
        assignments.save(new ClassTeacherAssignment(classId, request.teacherId(), effectiveFrom));
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
