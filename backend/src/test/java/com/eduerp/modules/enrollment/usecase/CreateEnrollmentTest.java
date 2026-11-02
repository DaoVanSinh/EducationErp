package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.TestAccess;
import com.eduerp.modules.courses.TestClassInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.ClassFullException;
import com.eduerp.modules.enrollment.ClassNotActiveException;
import com.eduerp.modules.enrollment.ClassNotFoundException;
import com.eduerp.modules.enrollment.DuplicateActiveEnrollmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.ScheduleConflictException;
import com.eduerp.modules.enrollment.StudentNotActiveException;
import com.eduerp.modules.enrollment.StudentProfileNotFoundException;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.internal.ScheduleConflictChecker;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreateEnrollmentTest {

    private static final int MAX_SEATS = 12;

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final StudentsManagement students = mock(StudentsManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    /** "Hôm nay" chốt trước mọi lịch học trong test để không buổi nào bị coi là đã qua. */
    private final ScheduleConflictChecker scheduleConflicts = new ScheduleConflictChecker(enrollments, courses,
            Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneOffset.UTC));
    private final CreateEnrollment useCase = new CreateEnrollment(enrollments, students, courses,
            TestAccess.organizationWide(), scheduleConflicts, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID classBranchId = UUID.randomUUID();
    private final UUID classTeacherId = UUID.randomUUID();

    private CreateEnrollmentRequest request() {
        return new CreateEnrollmentRequest(studentProfileId, classId);
    }

    private void stubActiveStudent() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.of(
                new StudentsManagement.StudentSummaryResponse(studentProfileId, UUID.randomUUID(), true)));
    }

    private void stubActiveClass() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                TestClassInfos.of(classId, courseId, classBranchId, classTeacherId,
                        MAX_SEATS, true, 24, List.of())));
    }

    private void stubSeatsTaken(long taken) {
        when(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .thenReturn(taken);
    }

    private void stubSaveEchoesBack() {
        when(enrollments.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownStudentProfile() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(StudentProfileNotFoundException.class);
    }

    @Test
    void rejectsADeactivatedStudentProfile() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.of(
                new StudentsManagement.StudentSummaryResponse(studentProfileId, UUID.randomUUID(), false)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(StudentNotActiveException.class);
    }

    @Test
    void rejectsAnUnknownClass() {
        stubActiveStudent();
        when(courses.getClassInfo(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void rejectsADeactivatedClass() {
        stubActiveStudent();
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                TestClassInfos.of(classId, courseId, classBranchId, classTeacherId,
                        MAX_SEATS, false, 24, List.of())));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassNotActiveException.class);
    }

    /** Review Focus #2, ranh giới dưới: chỗ thứ {@code maxSeats - 1} vẫn ghi danh được. */
    @Test
    void acceptsTheSeatBeforeLast() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS - 2);
        stubSaveEchoesBack();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request())).doesNotThrowAnyException();
    }

    /** Review Focus #2, ranh giới chính xác: chỗ thứ {@code maxSeats} (tức đang có maxSeats-1 chỗ đã
     * chiếm) PHẢI ghi danh được - chặn ở đây là chặn sai một chỗ hợp lệ. */
    @Test
    void acceptsTheVeryLastSeat() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS - 1);
        stubSaveEchoesBack();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request())).doesNotThrowAnyException();
    }

    /** Review Focus #2, ranh giới trên: chỗ thứ {@code maxSeats + 1} bị chặn. */
    @Test
    void rejectsTheSeatPastCapacity() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassFullException.class)
                .hasMessageContaining(String.valueOf(MAX_SEATS));
    }

    /** Review Focus #1, tầng usecase: lần ghi danh thứ hai khi lần đầu còn ACTIVE bị chặn TRƯỚC khi
     * chạm DB, để người dùng nhận ENROLLMENT_DUPLICATE_ACTIVE chứ không phải lỗi ràng buộc thô. */
    @Test
    void rejectsASecondActiveEnrollmentForTheSameStudentAndClass() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(1);
        when(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(DuplicateActiveEnrollmentException.class);
    }

    /** Học viên đã chiếm chỗ cuối của một lớp đầy mà ghi danh lại phải nhận "đã ghi danh", không
     * phải "lớp đã đủ chỗ" - kiểm trùng đứng TRƯỚC kiểm sĩ số. */
    @Test
    void reportsDuplicateRatherThanClassFullWhenTheSameStudentReEnrollsIntoAFullClass() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS);
        when(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(DuplicateActiveEnrollmentException.class);
    }

    @Test
    void snapshotsCourseAndBranchFromTheClassAndPublishesEnrollmentCreated() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(0);
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request());

        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.classId()).isEqualTo(classId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.branchId()).isEqualTo(classBranchId);
        assertThat(response.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(response.enrolledAt()).isNotNull();
        assertThat(response.withdrawnAt()).isNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentCreated.class));
        verify(events, never()).publishEvent(any(EnrollmentEvents.ScheduleConflictAccepted.class));
    }

    /** Lớp đang xét và lớp học viên đang học đụng nhau đúng một buổi 30/06/2026. */
    private void stubOneSessionConflict() {
        var currentClassId = UUID.randomUUID();
        var currentEnrollment = new Enrollment(studentProfileId, currentClassId, UUID.randomUUID(), classBranchId,
                actorAccountId);
        when(enrollments.findAllByStudentProfileIdAndStatus(studentProfileId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).thenReturn(List.of(currentEnrollment));
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(classOn(classId, CoursesConstants.ClassStatus.ENROLLING,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 28))));
        when(courses.classInfosOf(List.of(currentClassId))).thenReturn(List.of(classOn(currentClassId,
                CoursesConstants.ClassStatus.IN_PROGRESS, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 30))));
        when(courses.classSummariesOf(List.of(currentClassId))).thenReturn(Map.of(currentClassId,
                new CoursesManagement.ClassBasicSummary(currentClassId, "HE-TOAN", UUID.randomUUID(), "Toán hè")));
    }

    private CoursesManagement.ClassInfoResponse classOn(UUID id, CoursesConstants.ClassStatus status, LocalDate start,
            LocalDate end) {
        return new CoursesManagement.ClassInfoResponse(id, courseId, classBranchId, classTeacherId, MAX_SEATS, 1, status,
                start, end, null, null, 24, List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.TUE,
                        LocalTime.of(18, 0), LocalTime.of(20, 0))));
    }

    @Test
    void rejectsAScheduleConflictAndNamesTheClassAndTheSession() {
        stubActiveStudent();
        stubOneSessionConflict();
        stubSeatsTaken(0);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ScheduleConflictException.class)
                .hasMessageContaining("HE-TOAN")
                .hasMessageContaining("1 buổi")
                .hasMessageContaining("30/06/2026 18:00-20:00");
        verify(enrollments, never()).save(any(Enrollment.class));
    }

    @Test
    void savesAndAuditsWhenTheStaffAcceptsTheConflict() {
        stubActiveStudent();
        stubOneSessionConflict();
        stubSeatsTaken(0);
        stubSaveEchoesBack();

        useCase.execute(actorAccountId, actorBranchId, new CreateEnrollmentRequest(studentProfileId, classId, true));

        verify(enrollments).save(any(Enrollment.class));
        verify(events).publishEvent(any(EnrollmentEvents.ScheduleConflictAccepted.class));
    }

    /** Nhân viên chi nhánh không tự quyết được: gửi cờ xác nhận vẫn bị chặn và được hướng dẫn liên hệ quản trị viên. */
    @Test
    void branchStaffCannotAcceptAConflictAndIsToldToContactTheAdmin() {
        var branchStaffUseCase = new CreateEnrollment(enrollments, students, courses,
                TestAccess.withScope(AccessConstants.PermissionScope.BRANCH, classBranchId), scheduleConflicts, events);
        stubActiveStudent();
        stubOneSessionConflict();
        stubSeatsTaken(0);

        assertThatThrownBy(() -> branchStaffUseCase.execute(actorAccountId, actorBranchId,
                new CreateEnrollmentRequest(studentProfileId, classId, true)))
                .isInstanceOf(ScheduleConflictException.class)
                .hasMessageContaining("liên hệ quản trị viên để được tư vấn đổi lịch")
                .extracting("errorCode").isEqualTo(EnrollmentConstants.ErrorCodes.SCHEDULE_CONFLICT_CONTACT_ADMIN);
        verify(enrollments, never()).save(any(Enrollment.class));
    }

    /** Lớp đã đóng thì ghi danh cũ không còn buổi nào để trùng, dù ngày tháng có đè lên nhau. */
    @Test
    void ignoresClassesThatAreNoLongerOpen() {
        stubActiveStudent();
        stubOneSessionConflict();
        var closedClassId = UUID.randomUUID();
        when(enrollments.findAllByStudentProfileIdAndStatus(studentProfileId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).thenReturn(List.of(new Enrollment(studentProfileId,
                        closedClassId, UUID.randomUUID(), classBranchId, actorAccountId)));
        when(courses.classInfosOf(List.of(closedClassId))).thenReturn(List.of(classOn(closedClassId,
                CoursesConstants.ClassStatus.COMPLETED, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 30))));
        stubSeatsTaken(0);
        stubSaveEchoesBack();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request())).doesNotThrowAnyException();
    }

    /** Lớp thay thế phải cùng chi nhánh, còn chỗ và không đụng lớp đang học. */
    @Test
    void suggestsOnlyClassesOfTheSameCourseThatAreFreeAndHaveSeats() {
        stubActiveStudent();
        stubOneSessionConflict();
        stubSeatsTaken(0);
        var freeClassId = UUID.randomUUID();
        var free = new CoursesManagement.ClassInfoResponse(freeClassId, courseId, classBranchId, classTeacherId,
                MAX_SEATS, 1, CoursesConstants.ClassStatus.ENROLLING, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1),
                null, null, 24, List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.TUE, LocalTime.of(18, 0),
                        LocalTime.of(20, 0))));
        when(courses.listOpenClassesOfCourse(courseId)).thenReturn(List.of(free));
        when(courses.classSummariesOf(List.of(freeClassId))).thenReturn(Map.of(freeClassId,
                new CoursesManagement.ClassBasicSummary(freeClassId, "HE-VAN-02", courseId, "Văn hè")));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ScheduleConflictException.class)
                .hasMessageContaining("Lớp cùng khóa không trùng lịch: HE-VAN-02");
    }
}
