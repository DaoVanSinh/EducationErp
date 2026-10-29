package com.eduerp.modules.attendance.usecase;

import com.eduerp.modules.courses.TestClassInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.attendance.AttendanceConstants.AttendanceStatus;
import com.eduerp.modules.attendance.AttendanceEvents;
import com.eduerp.modules.attendance.AttendanceException;
import com.eduerp.modules.attendance.dto.AttendanceEntryRequest;
import com.eduerp.modules.attendance.dto.RecordAttendanceRequest;
import com.eduerp.modules.attendance.internal.access.ClassAttendanceAccessGuard;
import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecordAttendanceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    private final AttendanceSessionRepository sessions = mock(AttendanceSessionRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final ClassAttendanceAccessGuard guard = mock(ClassAttendanceAccessGuard.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(TODAY.atTime(9, 0).atZone(ZONE).toInstant(), ZONE);
    private final RecordAttendance useCase = new RecordAttendance(sessions, enrollments, guard, events, clock);

    private final UUID actor = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID anEnrollment = UUID.randomUUID();
    private final UUID otherEnrollment = UUID.randomUUID();

    @BeforeEach
    void rosterHasTwoStudents() {
        // Lớp đang học, khai giảng từ lâu - mọi ngày trong test đều nằm sau ngày khai giảng.
        when(guard.requireAccessToClass(any(), any(), any())).thenReturn(
                TestClassInfos.of(classId, UUID.randomUUID(), branch, actor, 20, true, 24, List.of()));
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(
                roster(anEnrollment), roster(otherEnrollment)));
        when(sessions.save(any(AttendanceSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static EnrollmentManagement.ClassRosterEntry roster(UUID enrollmentId) {
        return new EnrollmentManagement.ClassRosterEntry(enrollmentId, UUID.randomUUID(), UUID.randomUUID(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null);
    }

    private RecordAttendanceRequest request(LocalDate date, AttendanceEntryRequest... entries) {
        return new RecordAttendanceRequest(classId, date, null, List.of(entries));
    }

    private AttendanceEntryRequest entry(UUID enrollmentId, AttendanceStatus status) {
        return new AttendanceEntryRequest(enrollmentId, status, null);
    }

    @Test
    void createsANewSessionWithEveryEntryAndPublishesTheEvent() {
        when(sessions.findByClassIdAndSessionDate(classId, TODAY)).thenReturn(Optional.empty());

        useCase.execute(actor, branch, request(TODAY, entry(anEnrollment, AttendanceStatus.PRESENT),
                entry(otherEnrollment, AttendanceStatus.ABSENT)));

        var saved = ArgumentCaptor.forClass(AttendanceSession.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getRecords()).hasSize(2);
        verify(guard).requireAccessToClass(actor, classId, AccessConstants.Actions.UPDATE);
        verify(events).publishEvent(any(AttendanceEvents.AttendanceRecorded.class));
    }

    @Test
    void updatesTheExistingSessionOfThatDayInsteadOfCreatingASecondOne() {
        var existing = new AttendanceSession(classId, TODAY, null, actor);
        existing.mark(anEnrollment, AttendanceStatus.ABSENT, null);
        when(sessions.findByClassIdAndSessionDate(classId, TODAY)).thenReturn(Optional.of(existing));

        useCase.execute(actor, branch, request(TODAY, entry(anEnrollment, AttendanceStatus.LATE)));

        assertThat(existing.getRecords()).hasSize(1);
        assertThat(existing.getRecords().get(0).getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void rejectsADateThatHasNotComeYet() {
        assertThatThrownBy(() -> useCase.execute(actor, branch,
                request(TODAY.plusDays(1), entry(anEnrollment, AttendanceStatus.PRESENT))))
                .isInstanceOf(AttendanceException.FutureSessionDate.class);
        verify(sessions, never()).save(any());
    }

    /** Lớp chưa khai giảng (hoặc đã kết thúc/huỷ) không có buổi học nào để điểm danh. */
    @Test
    void rejectsAClassThatIsNotInProgress() {
        when(guard.requireAccessToClass(any(), any(), any())).thenReturn(TestClassInfos.withStatus(classId,
                UUID.randomUUID(), branch, actor, com.eduerp.modules.courses.CoursesConstants.ClassStatus.ENROLLING));

        assertThatThrownBy(() -> useCase.execute(actor, branch,
                request(TODAY, entry(anEnrollment, AttendanceStatus.PRESENT))))
                .isInstanceOf(AttendanceException.ClassNotInProgress.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void rejectsADateBeforeTheClassStartDate() {
        var startedToday = new com.eduerp.modules.courses.CoursesManagement.ClassInfoResponse(classId,
                UUID.randomUUID(), branch, actor, 20, 1, com.eduerp.modules.courses.CoursesConstants.ClassStatus.IN_PROGRESS,
                TODAY, null, Instant.now(), null, 24, List.of());
        when(guard.requireAccessToClass(any(), any(), any())).thenReturn(startedToday);

        assertThatThrownBy(() -> useCase.execute(actor, branch,
                request(TODAY.minusDays(1), entry(anEnrollment, AttendanceStatus.PRESENT))))
                .isInstanceOf(AttendanceException.BeforeClassStart.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void rejectsAStudentWhoIsNotInTheClass() {
        assertThatThrownBy(() -> useCase.execute(actor, branch,
                request(TODAY, entry(UUID.randomUUID(), AttendanceStatus.PRESENT))))
                .isInstanceOf(AttendanceException.EnrollmentNotInClass.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void rejectsTheSameStudentTwiceInOneSession() {
        assertThatThrownBy(() -> useCase.execute(actor, branch, request(TODAY,
                entry(anEnrollment, AttendanceStatus.PRESENT), entry(anEnrollment, AttendanceStatus.ABSENT))))
                .isInstanceOf(AttendanceException.DuplicateEntry.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void doesNothingWhenTheGuardRefusesTheCaller() {
        when(guard.requireAccessToClass(actor, classId, AccessConstants.Actions.UPDATE))
                .thenThrow(new AttendanceException.NotAssignedToClass(actor, classId));

        assertThatThrownBy(() -> useCase.execute(actor, branch,
                request(TODAY, entry(anEnrollment, AttendanceStatus.PRESENT))))
                .isInstanceOf(AttendanceException.NotAssignedToClass.class);
        verify(sessions, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
