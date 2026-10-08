package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
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
    private final CreateEnrollment useCase = new CreateEnrollment(enrollments, students, courses, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID classBranchId = UUID.randomUUID();

    private CreateEnrollmentRequest request() {
        return new CreateEnrollmentRequest(studentProfileId, classId);
    }

    private void stubActiveStudent() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.of(
                new StudentsManagement.StudentSummaryResponse(studentProfileId, UUID.randomUUID(), true)));
    }

    private void stubActiveClass() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, MAX_SEATS, true)));
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
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, MAX_SEATS, false)));

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
    }
}
