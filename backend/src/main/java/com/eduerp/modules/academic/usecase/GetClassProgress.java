package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiến trình học tập của một lớp (spec mục 5). KHÔNG LƯU gì: con số được tính lúc đọc từ
 * {@code enrolledAt}/{@code withdrawnAt} của từng lượt ghi danh đối chiếu lịch tuần của lớp - không
 * ai nhập tiến trình, và không có cột DB nào giữ nó.
 *
 * <p>Đây là TẦNG TÍNH TOÁN DUY NHẤT cho tiến trình: {@code ExportClassProgress} gọi lại chính
 * usecase này rồi serialize, nên số trên web và số trong file Excel không thể lệch nhau (spec mục
 * 11.7).
 *
 * <p>Đồng hồ nhận qua constructor theo tiền lệ {@code VnPayPaymentGatewayClient}: mốc kết thúc của
 * một ghi danh còn học là "bây giờ", nên không chốt được đồng hồ thì không test được rằng ghi danh
 * đã rút KHÔNG dùng mốc đó (Review Focus #2).
 */
@Service
public class GetClassProgress {

    private final EnrollmentManagement enrollments;
    private final ClassGradeAccessGuard guard;
    private final StudentNameResolver studentNames;
    private final Clock clock;

    @Autowired
    GetClassProgress(EnrollmentManagement enrollments, ClassGradeAccessGuard guard,
            StudentNameResolver studentNames) {
        this(enrollments, guard, studentNames, Clock.systemUTC());
    }

    /** Constructor cho test: tiến trình phụ thuộc "bây giờ" nên phải chốt được Clock. */
    GetClassProgress(EnrollmentManagement enrollments, ClassGradeAccessGuard guard,
            StudentNameResolver studentNames, Clock clock) {
        this.enrollments = enrollments;
        this.guard = guard;
        this.studentNames = studentNames;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClassProgressResponse execute(UUID actorAccountId, UUID classId) {
        var classInfo = guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listEnrollmentsByClass(classId);
        var names = studentNames.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());
        var now = clock.instant();

        var rows = roster.stream()
                .map(entry -> toRow(entry, classInfo, names, now))
                .toList();
        return new ClassProgressResponse(classId, classInfo.courseId(),
                classInfo.standardSessionCount(), rows);
    }

    private static StudentProgressRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            CoursesManagement.ClassInfoResponse classInfo, Map<UUID, String> names, Instant now) {
        var elapsedSessions = ProgressRules.countAttendedSessions(classInfo.schedule(), entry.enrolledAt(),
                endMarkOf(entry, now), classInfo.startedAt(), classInfo.endedAt(), ProgressRules.centerZone());
        return new StudentProgressRow(entry.enrollmentId(), entry.studentProfileId(),
                names.get(entry.studentProfileId()), entry.status(), entry.enrolledAt(),
                entry.withdrawnAt(), elapsedSessions, classInfo.standardSessionCount(),
                ProgressRules.progressPercent(elapsedSessions, classInfo.standardSessionCount()));
    }

    /**
     * Spec mục 5: "mốc_kết_thúc = Enrollment.withdrawnAt nếu status == WITHDRAWN, ngược lại là thời
     * điểm hiện tại". {@code withdrawnAt} null trên một bản ghi WITHDRAWN là dữ liệu lệch (V17 cho
     * phép null vì ghi danh ACTIVE chưa có mốc rút), nên vẫn rơi về "bây giờ" thay vì ném NPE.
     */
    private static Instant endMarkOf(EnrollmentManagement.ClassRosterEntry entry, Instant now) {
        if (entry.status() == EnrollmentConstants.EnrollmentStatus.WITHDRAWN
                && entry.withdrawnAt() != null) {
            return entry.withdrawnAt();
        }
        return now;
    }
}
