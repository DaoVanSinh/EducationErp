package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.academic.EnrollmentNotFoundException;
import com.eduerp.modules.academic.GradeColumnNotInCourseException;
import com.eduerp.modules.academic.InvalidScoreException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecordGradeTest {

    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordGrade useCase = new RecordGrade(gradeEntries, enrollments, courses, guard, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID midtermColumnId = UUID.randomUUID();

    private RecordGradeRequest request(String score) {
        return new RecordGradeRequest(classId, enrollmentId, midtermColumnId, new BigDecimal(score));
    }

    private void stubAccessGranted() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.UPDATE))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, actorBranchId,
                        actorAccountId, 20, true, 24, List.of()));
    }

    private void stubRosterContainsTheEnrolment() {
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentId, UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
    }

    private void stubColumnOutOf(String maxScore) {
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(new GradeColumnResponse(
                midtermColumnId, "Giữa kỳ", new BigDecimal("40.00"), new BigDecimal(maxScore), 0)));
    }

    private void stubNoExistingEntry() {
        when(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId))
                .thenReturn(Optional.empty());
    }

    private void stubHappyPath() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");
        stubNoExistingEntry();
    }

    @Test
    void savesANewEntryAndPublishesGradeRecorded() {
        stubHappyPath();

        useCase.execute(actorAccountId, actorBranchId, request("8.50"));

        var saved = ArgumentCaptor.forClass(GradeEntry.class);
        verify(gradeEntries).save(saved.capture());
        assertThat(saved.getValue().getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(saved.getValue().getCourseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(saved.getValue().getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(saved.getValue().getEnteredByAccountId()).isEqualTo(actorAccountId);
        verify(events).publishEvent(any(AcademicEvents.GradeRecorded.class));
    }

    /** Spec mục 6: UNIQUE (enrollment, column) nên nhập lại là SỬA dòng cũ, không tạo dòng thứ hai. */
    @Test
    void updatesTheExistingEntryInsteadOfInsertingASecondRow() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");
        var existing = new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("7.00"),
                UUID.randomUUID());
        when(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId))
                .thenReturn(Optional.of(existing));

        useCase.execute(actorAccountId, actorBranchId, request("9.00"));

        assertThat(existing.getScore()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(existing.getEnteredByAccountId()).isEqualTo(actorAccountId);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
        verify(events).publishEvent(any(AcademicEvents.GradeRecorded.class));
    }

    /** Review Focus #4: guard chặn TRƯỚC khi chạm vào dữ liệu nào - không được lưu nửa vời rồi mới
     * phát hiện sai quyền. */
    @Test
    void doesNotTouchAnythingWhenTheGuardRefusesTheCaller() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.UPDATE))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(NotAssignedToClassException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
        verify(events, never()).publishEvent(any());
    }

    /** Lượt ghi danh không thuộc lớp đang nhập điểm: chặn, không âm thầm ghi điểm cho lớp khác. */
    @Test
    void rejectsAnEnrolmentThatDoesNotBelongToThisClass() {
        stubAccessGranted();
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(UUID.randomUUID(), UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(EnrollmentNotFoundException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    /** Review Focus #11: cột điểm thuộc khóa khác (hoặc không tồn tại) bị chặn - một điểm gắn vào cột
     * ngoài khóa sẽ không bao giờ được tính vào điểm tổng kết của ai. */
    @Test
    void rejectsAColumnBelongingToAnotherCourse() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(new GradeColumnResponse(
                UUID.randomUUID(), "Cột của khóa khác", new BigDecimal("100.00"),
                new BigDecimal("10.00"), 0)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(GradeColumnNotInCourseException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào thì không có gì để nhập - cùng lỗi với cột lạ. */
    @Test
    void rejectsRecordingWhenTheCourseHasNoGradeColumnsConfigured() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(GradeColumnNotInCourseException.class);
    }

    /** Review Focus #10: thang của ĐÚNG cột đó quyết định. 9.50 hợp lệ ở thang 10... */
    @Test
    void acceptsAScoreWithinTheColumnRange() {
        stubHappyPath();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("9.50")))
                .doesNotThrowAnyException();
    }

    /** ...và sai ở thang 5, dù vẫn là cùng một con số. */
    @Test
    void rejectsAScoreAboveTheMaxScoreOfThatColumn() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("5.00");

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("9.50")))
                .isInstanceOf(InvalidScoreException.class)
                .hasMessageContaining("5.00");
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    @Test
    void rejectsANegativeScore() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("-0.01")))
                .isInstanceOf(InvalidScoreException.class);
    }

    /** Hai ranh giới hợp lệ: đúng 0 và đúng maxScore phải nhận được, chặn ở đây là chặn sai. */
    @Test
    void acceptsExactlyZeroAndExactlyTheMaxScore() {
        stubHappyPath();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("0.00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("10.00")))
                .doesNotThrowAnyException();
    }

    /** Học viên đã rút vẫn sửa được điểm đã có: trung tâm cần chốt lại bảng điểm cuối khóa cho người
     * học dở, và spec không nói rút khỏi lớp thì khoá luôn việc nhập điểm. */
    @Test
    void stillRecordsGradesForAWithdrawnEnrolment() {
        stubAccessGranted();
        stubColumnOutOf("10.00");
        stubNoExistingEntry();
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentId, UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.WITHDRAWN, Instant.now(), Instant.now())));

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("8.00")))
                .doesNotThrowAnyException();
    }

    /** Sự kiện phải mang đủ bốn field spec mục 9 yêu cầu, nếu không dòng audit mất actor/chi nhánh. */
    @Test
    void publishesAnEventCarryingTheEnrolmentColumnActorAndBranch() {
        stubHappyPath();

        useCase.execute(actorAccountId, actorBranchId, request("8.50"));

        var published = ArgumentCaptor.forClass(AcademicEvents.GradeRecorded.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().enrollmentId()).isEqualTo(enrollmentId);
        assertThat(published.getValue().courseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(published.getValue().actorAccountId()).isEqualTo(actorAccountId);
        assertThat(published.getValue().actorBranchId()).isEqualTo(actorBranchId);
    }
}
