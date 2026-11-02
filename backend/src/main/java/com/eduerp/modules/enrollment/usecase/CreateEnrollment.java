package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.ClassFullException;
import com.eduerp.modules.enrollment.ClassNotActiveException;
import com.eduerp.modules.enrollment.ClassNotFoundException;
import com.eduerp.modules.enrollment.DuplicateActiveEnrollmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.ScheduleConflictException;
import com.eduerp.modules.enrollment.StudentNotActiveException;
import com.eduerp.modules.enrollment.StudentProfileNotFoundException;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.ScheduleConflictChecker;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thứ tự kiểm tra đúng theo spec mục 4: học viên → lớp → trùng ghi danh → sĩ số → trùng lịch → lưu → publish.
 * Trùng lịch kiểm sau sĩ số: gợi ý đổi lớp không có nghĩa nếu lớp đã hết chỗ ngay từ đầu.
 */
@Service
public class CreateEnrollment {

    private final EnrollmentRepository enrollments;
    private final StudentsManagement students;
    private final CoursesManagement courses;
    private final AccessManagement access;
    private final ScheduleConflictChecker scheduleConflicts;
    private final ApplicationEventPublisher events;

    CreateEnrollment(EnrollmentRepository enrollments, StudentsManagement students, CoursesManagement courses,
            AccessManagement access, ScheduleConflictChecker scheduleConflicts, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.students = students;
        this.courses = courses;
        this.access = access;
        this.scheduleConflicts = scheduleConflicts;
        this.events = events;
    }

    @Transactional
    public EnrollmentResponse execute(UUID actorAccountId, UUID actorBranchId, CreateEnrollmentRequest request) {
        var student = students.getProfile(request.studentProfileId())
                .orElseThrow(() -> new StudentProfileNotFoundException(request.studentProfileId()));
        if (!student.active()) {
            throw new StudentNotActiveException(request.studentProfileId());
        }

        var classInfo = courses.getClassInfo(request.classId())
                .orElseThrow(() -> new ClassNotFoundException(request.classId()));
        // Lớp 2 (spec 6.4): người chỉ có quyền theo chi nhánh chỉ ghi danh vào lớp của chi nhánh mình.
        var scope = access.dataScopeOf(actorAccountId, AccessConstants.Resources.ENROLLMENT,
                AccessConstants.Actions.CREATE);
        scope.requireCovers(classInfo.branchId());
        // Nhận ghi danh khi lớp đang tuyển sinh hoặc đang học (vào học muộn); đã kết thúc/huỷ thì không.
        if (!classInfo.acceptsEnrollment()) {
            throw new ClassNotActiveException(request.classId());
        }

        // Lớp phòng thủ thứ nhất của Review Focus #1 - partial unique index trong V17 là lớp thứ hai.
        // Kiểm TRƯỚC sĩ số: học viên đã ghi danh lớp đầy mà bấm ghi danh lại phải nhận đúng lỗi "đã
        // ghi danh", không phải "lớp đã đủ chỗ" (chính học viên đó đang chiếm một chỗ).
        if (enrollments.existsByStudentProfileIdAndClassIdAndStatus(request.studentProfileId(), request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE)) {
            throw new DuplicateActiveEnrollmentException(request.studentProfileId(), request.classId());
        }

        // >= maxSeats: đúng ranh giới ở Review Focus #2 - khi đã có maxSeats chỗ ACTIVE thì chỗ tiếp
        // theo là chỗ thứ maxSeats + 1, không còn chỗ.
        var activeSeats = enrollments.countByClassIdAndStatus(request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        if (activeSeats >= classInfo.maxSeats()) {
            throw new ClassFullException(request.classId(), classInfo.maxSeats());
        }

        // Chỉ quản trị viên (quyền toàn tổ chức) được chấp nhận trùng lịch; nhân viên chi nhánh gửi cờ cũng không qua.
        var conflict = scheduleConflicts.describeConflicts(request.studentProfileId(), classInfo);
        if (conflict.isPresent() && !scope.isOrganizationWide()) {
            throw ScheduleConflictException.requiresAdmin(conflict.get());
        }
        if (conflict.isPresent() && !request.acceptScheduleConflict()) {
            throw ScheduleConflictException.acceptable(conflict.get());
        }

        var saved = enrollments.save(new Enrollment(request.studentProfileId(), request.classId(),
                classInfo.courseId(), classInfo.branchId(), actorAccountId));
        events.publishEvent(new EnrollmentEvents.EnrollmentCreated(saved.getId(), actorAccountId, actorBranchId));
        if (conflict.isPresent()) {
            events.publishEvent(
                    new EnrollmentEvents.ScheduleConflictAccepted(saved.getId(), actorAccountId, actorBranchId));
        }
        return toResponse(saved);
    }

    /** Dùng lại ở WithdrawEnrollment/CompleteEnrollment/ListEnrollments - một chỗ map duy nhất. */
    static EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getClassId(),
                enrollment.getCourseId(), enrollment.getBranchId(), enrollment.getStatus(),
                enrollment.getEnrolledAt(), enrollment.getWithdrawnAt());
    }
}
