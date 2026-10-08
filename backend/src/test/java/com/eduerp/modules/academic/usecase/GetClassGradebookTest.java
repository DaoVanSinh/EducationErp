package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetClassGradebookTest {

    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final StudentNameResolver names = mock(StudentNameResolver.class);
    private final GetClassGradebook useCase =
            new GetClassGradebook(gradeEntries, enrollments, courses, guard, names);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();
    private final UUID enrollmentA = UUID.randomUUID();
    private final UUID enrollmentB = UUID.randomUUID();
    private final UUID studentA = UUID.randomUUID();
    private final UUID studentB = UUID.randomUUID();

    private void stubAccessGranted() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, UUID.randomUUID(),
                        actorAccountId, 20, true, 24, List.of()));
    }

    private void stubTwoColumns() {
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(
                new GradeColumnResponse(midtermId, "Giữa kỳ", new BigDecimal("40.00"),
                        new BigDecimal("10.00"), 0),
                new GradeColumnResponse(finalId, "Cuối kỳ", new BigDecimal("60.00"),
                        new BigDecimal("10.00"), 1)));
    }

    private void stubTwoStudents() {
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null),
                new EnrollmentManagement.ClassRosterEntry(enrollmentB, studentB, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
        when(names.namesOf(List.of(studentA, studentB)))
                .thenReturn(Map.of(studentA, "Nguyễn Văn A", studentB, "Trần Thị B"));
    }

    private static GradeEntry entry(UUID enrollmentId, UUID columnId, String score) {
        return new GradeEntry(enrollmentId, columnId, new BigDecimal(score), UUID.randomUUID());
    }

    private StudentGradesRow rowOf(UUID enrollmentId, List<StudentGradesRow> rows) {
        return rows.stream().filter(row -> row.enrollmentId().equals(enrollmentId)).findFirst()
                .orElseThrow();
    }

    @Test
    void buildsTheGradebookWithColumnsScoresAndNames() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB))).thenReturn(List.of(
                entry(enrollmentA, midtermId, "8.00"), entry(enrollmentA, finalId, "9.00"),
                entry(enrollmentB, midtermId, "6.00"), entry(enrollmentB, finalId, "7.00")));

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.classId()).isEqualTo(classId);
        assertThat(gradebook.courseId()).isEqualTo(courseId);
        assertThat(gradebook.columns()).extracting(GradeColumnResponse::name)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
        assertThat(gradebook.rows()).hasSize(2);
        var rowA = rowOf(enrollmentA, gradebook.rows());
        assertThat(rowA.studentName()).isEqualTo("Nguyễn Văn A");
        assertThat(rowA.scoresByColumnId().get(midtermId)).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(rowA.finalScore())
                .as("8/10*40 + 9/10*60 = 86")
                .isEqualByComparingTo(new BigDecimal("86.00"));
        assertThat(rowOf(enrollmentB, gradebook.rows()).finalScore())
                .as("6/10*40 + 7/10*60 = 66")
                .isEqualByComparingTo(new BigDecimal("66.00"));
    }

    /**
     * Review Focus #3: thiếu một cột → {@code finalScore} null, KHÔNG phải 32.00 (giá trị mà một
     * phép "trung bình trên phần đã có" sẽ trả ra và người xem sẽ đọc là điểm trượt).
     */
    @Test
    void leavesFinalScoreNullForAStudentMissingOneColumn() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB))).thenReturn(List.of(
                entry(enrollmentA, midtermId, "8.00"),
                entry(enrollmentB, midtermId, "6.00"), entry(enrollmentB, finalId, "7.00")));

        var gradebook = useCase.execute(actorAccountId, classId);

        var rowA = rowOf(enrollmentA, gradebook.rows());
        assertThat(rowA.finalScore()).isNull();
        assertThat(rowA.scoresByColumnId())
                .as("điểm đã nhập vẫn hiện ra - chỉ điểm TỔNG KẾT là chưa có")
                .containsOnlyKeys(midtermId);
        assertThat(rowOf(enrollmentB, gradebook.rows()).finalScore()).isNotNull();
    }

    @Test
    void leavesEveryFinalScoreNullWhenNoGradeHasBeenEnteredYet() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB)))
                .thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).allSatisfy(row -> {
            assertThat(row.finalScore()).isNull();
            assertThat(row.scoresByColumnId()).isEmpty();
        });
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào - bảng vẫn dựng được (danh sách học viên có
     * nghĩa), chỉ là không có cột nào và không có điểm tổng kết nào. */
    @Test
    void returnsTheRosterWithNoColumnsWhenTheCourseHasNoneConfigured() {
        stubAccessGranted();
        stubTwoStudents();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB)))
                .thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.columns()).isEmpty();
        assertThat(gradebook.rows()).hasSize(2);
        assertThat(gradebook.rows()).allSatisfy(row -> assertThat(row.finalScore()).isNull());
    }

    /** Lớp chưa có học viên nào: bảng rỗng là câu trả lời hợp lệ, không ném. */
    @Test
    void returnsAnEmptyRowListForAClassWithoutAnyEnrolment() {
        stubAccessGranted();
        stubTwoColumns();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of());
        when(names.namesOf(List.of())).thenReturn(Map.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of())).thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).isEmpty();
        assertThat(gradebook.columns()).hasSize(2);
    }

    /** Hồ sơ học viên bị xoá/không tra được tên: dòng vẫn còn với tên trống, không làm đổ cả bảng. */
    @Test
    void keepsTheRowWhenAStudentNameCannotBeResolved() {
        stubAccessGranted();
        stubTwoColumns();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
        when(names.namesOf(List.of(studentA))).thenReturn(Map.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA))).thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).hasSize(1);
        assertThat(gradebook.rows().get(0).studentName()).isNull();
    }

    /** Review Focus #4: đường đọc cũng đi qua guard - không có endpoint xem bảng điểm nào bỏ qua
     * kiểm tra "đúng lớp". */
    @Test
    void refusesACallerTheGuardRejects() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
