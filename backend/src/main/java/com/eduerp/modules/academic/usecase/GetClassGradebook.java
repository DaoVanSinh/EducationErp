package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng điểm của một lớp (spec mục 6). Đây là TẦNG TÍNH TOÁN DUY NHẤT cho bảng điểm:
 * {@code ExportClassGradebook} gọi lại chính usecase này rồi serialize, nên số trên web và số trong
 * file Excel không thể lệch nhau (spec mục 11.7).
 *
 * <p>Bốn lần đọc cố định, không tăng theo số học viên (rule #3): một lần guard/lớp, một lần danh
 * sách ghi danh, một lần cột điểm, một lần điểm theo lô - cộng hai lần nạp tên theo lô bên trong
 * {@link StudentNameResolver}.
 */
@Service
public class GetClassGradebook {

    private final GradeEntryRepository gradeEntries;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final ClassGradeAccessGuard guard;
    private final StudentNameResolver studentNames;

    GetClassGradebook(GradeEntryRepository gradeEntries, EnrollmentManagement enrollments,
            CoursesManagement courses, ClassGradeAccessGuard guard, StudentNameResolver studentNames) {
        this.gradeEntries = gradeEntries;
        this.enrollments = enrollments;
        this.courses = courses;
        this.guard = guard;
        this.studentNames = studentNames;
    }

    @Transactional(readOnly = true)
    public GradebookResponse execute(UUID actorAccountId, UUID classId) {
        var classInfo = guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listEnrollmentsByClass(classId);
        var columns = courses.getGradeColumns(classInfo.courseId());
        var scores = scoresByEnrollment(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::enrollmentId).toList());
        var names = studentNames.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());

        var rows = roster.stream()
                .map(entry -> toRow(entry, columns, scores, names))
                .toList();
        return new GradebookResponse(classId, classInfo.courseId(), columns, rows);
    }

    private Map<UUID, Map<UUID, BigDecimal>> scoresByEnrollment(List<UUID> enrollmentIds) {
        return gradeEntries.findAllByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.groupingBy(GradeEntry::getEnrollmentId,
                        Collectors.toMap(GradeEntry::getCourseGradeColumnId, GradeEntry::getScore)));
    }

    private static StudentGradesRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            List<GradeColumnResponse> columns, Map<UUID, Map<UUID, BigDecimal>> scores,
            Map<UUID, String> names) {
        var rowScores = scores.getOrDefault(entry.enrollmentId(), Map.of());
        return new StudentGradesRow(entry.enrollmentId(), entry.studentProfileId(),
                names.get(entry.studentProfileId()), entry.status(), rowScores,
                GradeRules.finalScore(columns, rowScores));
    }
}
