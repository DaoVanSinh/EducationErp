package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.academic.ClassNotStartedException;
import com.eduerp.modules.academic.EnrollmentNotFoundException;
import com.eduerp.modules.academic.GradeColumnNotInCourseException;
import com.eduerp.modules.academic.InvalidScoreException;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập hoặc sửa điểm một cột cho một lượt ghi danh (spec mục 6). Upsert vì UNIQUE
 * {@code (enrollment_id, course_grade_column_id)} của V24: nhập lại là sửa dòng cũ.
 *
 * <p>Lượt ghi danh được xác nhận qua {@code listEnrollmentsByClass(classId)} chứ không qua
 * {@code getEnrollment(enrollmentId)}: record của method thứ hai không mang {@code classId} nên
 * không kiểm được "lượt ghi danh này có thuộc lớp đang nhập điểm không", và spec mục 3 chỉ cho thêm
 * MỘT method đọc vào facade enrollment. Một câu truy vấn trả về đúng danh sách lớp (tối đa
 * {@code maxSeats} dòng) vừa kiểm được điều đó vừa cho luôn {@code courseId} để tra cột điểm.
 */
@Service
public class RecordGrade {

    private final GradeEntryRepository gradeEntries;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final ClassGradeAccessGuard guard;
    private final ApplicationEventPublisher events;

    RecordGrade(GradeEntryRepository gradeEntries, EnrollmentManagement enrollments,
            CoursesManagement courses, ClassGradeAccessGuard guard, ApplicationEventPublisher events) {
        this.gradeEntries = gradeEntries;
        this.enrollments = enrollments;
        this.courses = courses;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID actorAccountId, UUID actorBranchId, RecordGradeRequest request) {
        var classInfo = guard.requireAccessToClass(actorAccountId, request.classId(), AccessConstants.Actions.UPDATE);
        // Điểm có từ khi lớp khai giảng; lớp đã kết thúc vẫn nhập/sửa được (điểm cuối khoá thường vào sau buổi cuối).
        if (classInfo.status() == CoursesConstants.ClassStatus.ENROLLING
                || classInfo.status() == CoursesConstants.ClassStatus.CANCELLED) {
            throw new ClassNotStartedException(request.classId());
        }
        var rosterEntry = enrollments.listEnrollmentsByClass(request.classId()).stream()
                .filter(entry -> entry.enrollmentId().equals(request.enrollmentId()))
                .findFirst()
                .orElseThrow(() -> new EnrollmentNotFoundException(request.enrollmentId()));
        var column = findColumn(rosterEntry.courseId(), request.courseGradeColumnId());
        if (!GradeRules.isScoreWithinRange(request.score(), column.maxScore())) {
            throw new InvalidScoreException(request.score(), column.maxScore());
        }
        upsert(request, actorAccountId);
        events.publishEvent(new AcademicEvents.GradeRecorded(request.enrollmentId(),
                request.courseGradeColumnId(), actorAccountId, actorBranchId));
    }

    private GradeColumnResponse findColumn(UUID courseId, UUID courseGradeColumnId) {
        return courses.getGradeColumns(courseId).stream()
                .filter(column -> column.id().equals(courseGradeColumnId))
                .findFirst()
                .orElseThrow(() -> new GradeColumnNotInCourseException(courseGradeColumnId, courseId));
    }

    private void upsert(RecordGradeRequest request, UUID actorAccountId) {
        gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(request.enrollmentId(),
                        request.courseGradeColumnId())
                .ifPresentOrElse(entry -> entry.changeScore(request.score(), actorAccountId),
                        () -> gradeEntries.save(new GradeEntry(request.enrollmentId(),
                                request.courseGradeColumnId(), request.score(), actorAccountId)));
    }
}
