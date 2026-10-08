package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.ClassFullException;
import com.eduerp.modules.enrollment.ClassNotActiveException;
import com.eduerp.modules.enrollment.ClassNotFoundException;
import com.eduerp.modules.enrollment.DuplicateActiveEnrollmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.StudentNotActiveException;
import com.eduerp.modules.enrollment.StudentProfileNotFoundException;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thứ tự kiểm tra đúng theo spec mục 4: học viên → lớp → sĩ số → trùng ghi danh → lưu → publish. */
@Service
public class CreateEnrollment {

    private final EnrollmentRepository enrollments;
    private final StudentsManagement students;
    private final CoursesManagement courses;
    private final ApplicationEventPublisher events;

    CreateEnrollment(EnrollmentRepository enrollments, StudentsManagement students, CoursesManagement courses,
            ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.students = students;
        this.courses = courses;
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
        if (!classInfo.active()) {
            throw new ClassNotActiveException(request.classId());
        }

        // >= maxSeats: đúng ranh giới ở Review Focus #2 - khi đã có maxSeats chỗ ACTIVE thì chỗ tiếp
        // theo là chỗ thứ maxSeats + 1, không còn chỗ.
        var activeSeats = enrollments.countByClassIdAndStatus(request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        if (activeSeats >= classInfo.maxSeats()) {
            throw new ClassFullException(request.classId(), classInfo.maxSeats());
        }

        // Lớp phòng thủ thứ nhất của Review Focus #1 - partial unique index trong V17 là lớp thứ hai.
        if (enrollments.existsByStudentProfileIdAndClassIdAndStatus(request.studentProfileId(), request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE)) {
            throw new DuplicateActiveEnrollmentException(request.studentProfileId(), request.classId());
        }

        var saved = enrollments.save(new Enrollment(request.studentProfileId(), request.classId(),
                classInfo.courseId(), classInfo.branchId(), actorAccountId));
        events.publishEvent(new EnrollmentEvents.EnrollmentCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    /** Dùng lại ở WithdrawEnrollment/CompleteEnrollment/ListEnrollments - một chỗ map duy nhất. */
    static EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getClassId(),
                enrollment.getCourseId(), enrollment.getBranchId(), enrollment.getStatus(),
                enrollment.getEnrolledAt(), enrollment.getWithdrawnAt());
    }
}
