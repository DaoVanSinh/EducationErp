package com.eduerp.modules.enrollment.internal;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.enrollment.internal.rules.ScheduleConflictRules;
import com.eduerp.modules.enrollment.internal.rules.ScheduleConflictRules.ClassPlan;
import com.eduerp.modules.enrollment.internal.rules.ScheduleConflictRules.ConflictingSession;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Đối chiếu lớp sắp ghi danh với các lớp học viên đang học, dựa trên lịch tuần đã lưu của từng lớp. Trả về câu
 * mô tả cho nhân viên: trùng với lớp nào, những buổi nào, và lớp nào cùng khóa có thể đổi sang.
 */
@Component
public class ScheduleConflictChecker {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EnrollmentRepository enrollments;
    private final CoursesManagement courses;
    private final Clock clock;

    @Autowired
    ScheduleConflictChecker(EnrollmentRepository enrollments, CoursesManagement courses) {
        this(enrollments, courses, Clock.system(ZoneId.of(EnrollmentConstants.Schedules.CENTER_ZONE_ID)));
    }

    /** Constructor cho test: "hôm nay" phải chốt được vì chỉ các buổi chưa diễn ra mới tính là trùng. */
    public ScheduleConflictChecker(EnrollmentRepository enrollments, CoursesManagement courses, Clock clock) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.clock = clock;
    }

    /** Câu mô tả chỗ trùng, hoặc rỗng nếu lớp {@code target} không đụng lớp nào học viên đang học. */
    public Optional<String> describeConflicts(UUID studentProfileId, CoursesManagement.ClassInfoResponse target) {
        var currentClasses = openClassesOf(studentProfileId).stream()
                .filter(current -> !current.classId().equals(target.classId()))
                .toList();
        var today = LocalDate.now(clock);
        return describeAgainst(target, currentClasses, today).map(details -> "Trùng lịch với " + details + ". "
                + describeAlternatives(target, currentClasses, today));
    }

    /**
     * Chỗ trùng giữa các lớp học viên đang học, theo id lớp; lớp không trùng thì không có trong kết quả. Cho cổng
     * học tập: ghi danh đã được chấp nhận trùng lịch thì học viên vẫn phải được báo.
     */
    public Map<UUID, String> describeCurrentConflicts(UUID studentProfileId) {
        var currentClasses = openClassesOf(studentProfileId);
        var today = LocalDate.now(clock);
        var described = new HashMap<UUID, String>();
        currentClasses.forEach(current -> describeAgainst(current, currentClasses.stream()
                .filter(other -> !other.classId().equals(current.classId()))
                .toList(), today).ifPresent(details -> described.put(current.classId(), details)));
        return described;
    }

    private Optional<String> describeAgainst(CoursesManagement.ClassInfoResponse target,
            List<CoursesManagement.ClassInfoResponse> others, LocalDate today) {
        var conflicts = others.stream()
                .collect(Collectors.toMap(Function.identity(), other -> sessionsInConflict(target, other, today)))
                .entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .toList();
        if (conflicts.isEmpty()) {
            return Optional.empty();
        }
        var names = courses.classSummariesOf(conflicts.stream().map(entry -> entry.getKey().classId()).toList());
        return Optional.of(conflicts.stream()
                .map(entry -> describeClass(names.get(entry.getKey().classId()), entry.getValue()))
                .sorted()
                .collect(Collectors.joining("; ")));
    }

    /** Lớp học viên đang học và còn mở - ghi danh ACTIVE của lớp đã đóng không còn buổi nào để trùng. */
    private List<CoursesManagement.ClassInfoResponse> openClassesOf(UUID studentProfileId) {
        var classIds = enrollments
                .findAllByStudentProfileIdAndStatus(studentProfileId, EnrollmentConstants.EnrollmentStatus.ACTIVE)
                .stream()
                .map(Enrollment::getClassId)
                .toList();
        return courses.classInfosOf(classIds).stream()
                .filter(CoursesManagement.ClassInfoResponse::acceptsEnrollment)
                .toList();
    }

    private static List<ConflictingSession> sessionsInConflict(CoursesManagement.ClassInfoResponse target,
            CoursesManagement.ClassInfoResponse current, LocalDate today) {
        return ScheduleConflictRules.findConflicts(planOf(target), planOf(current), today);
    }

    private static ClassPlan planOf(CoursesManagement.ClassInfoResponse info) {
        return new ClassPlan(info.startDate(), info.endDate(), info.standardSessionCount(), info.schedule());
    }

    private static String describeClass(CoursesManagement.ClassBasicSummary name, List<ConflictingSession> sessions) {
        return "lớp " + name.code() + " (" + name.courseName() + "): " + describeSessions(sessions);
    }

    private static String describeSessions(List<ConflictingSession> sessions) {
        if (sessions.size() > EnrollmentConstants.Limits.MAX_CONFLICT_DATES_LISTED) {
            return sessions.size() + " buổi, từ " + DATE.format(sessions.getFirst().date()) + " đến "
                    + DATE.format(sessions.getLast().date());
        }
        return sessions.size() + " buổi, " + sessions.stream()
                .map(session -> DATE.format(session.date()) + " " + TIME.format(session.startTime()) + "-"
                        + TIME.format(session.endTime()))
                .collect(Collectors.joining(", "));
    }

    /** Lớp cùng khóa, cùng chi nhánh, còn chỗ và không đụng lớp nào học viên đang học. */
    private String describeAlternatives(CoursesManagement.ClassInfoResponse target,
            List<CoursesManagement.ClassInfoResponse> currentClasses, LocalDate today) {
        var candidates = courses.listOpenClassesOfCourse(target.courseId()).stream()
                .filter(candidate -> !candidate.classId().equals(target.classId()))
                .filter(candidate -> candidate.branchId().equals(target.branchId()))
                .filter(candidate -> currentClasses.stream()
                        .noneMatch(current -> current.classId().equals(candidate.classId())
                                || !sessionsInConflict(candidate, current, today).isEmpty()))
                .toList();
        var withSeats = withFreeSeats(candidates);
        if (withSeats.isEmpty()) {
            return "Chi nhánh này chưa có lớp nào khác của khóa còn chỗ và không trùng lịch.";
        }
        var names = courses.classSummariesOf(withSeats);
        return "Lớp cùng khóa không trùng lịch: " + withSeats.stream()
                .map(classId -> names.get(classId).code())
                .sorted()
                .limit(EnrollmentConstants.Limits.MAX_ALTERNATIVE_CLASSES_LISTED)
                .collect(Collectors.joining(", ")) + ".";
    }

    private List<UUID> withFreeSeats(List<CoursesManagement.ClassInfoResponse> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        var classIds = candidates.stream().map(CoursesManagement.ClassInfoResponse::classId).toList();
        Map<UUID, Long> seatsTaken = enrollments
                .countByClassIdsAndStatus(classIds, EnrollmentConstants.EnrollmentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(EnrollmentRepository.ClassCountRow::getClassId,
                        EnrollmentRepository.ClassCountRow::getTotal));
        return candidates.stream()
                .filter(candidate -> seatsTaken.getOrDefault(candidate.classId(), 0L) < candidate.maxSeats())
                .map(CoursesManagement.ClassInfoResponse::classId)
                .toList();
    }
}
