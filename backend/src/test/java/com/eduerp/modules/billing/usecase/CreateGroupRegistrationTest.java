package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyDiscountedException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.GroupMemberMismatchException;
import com.eduerp.modules.billing.MinimumGroupSizeException;
import com.eduerp.modules.billing.dto.CreateGroupRegistrationRequest;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.model.GroupRegistration;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreateGroupRegistrationTest {

    private static final BigDecimal TUITION = new BigDecimal("4500000");

    private final GroupRegistrationRepository groups = mock(GroupRegistrationRepository.class);
    private final ComboDiscountTierRepository tiers = mock(ComboDiscountTierRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final EnrollmentDiscounts discounts = mock(EnrollmentDiscounts.class);
    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateGroupRegistration useCase = new CreateGroupRegistration(groups, tiers, invoices, discounts,
            enrollmentManagement, coursesManagement, events, mock(BillingBranchGuard.class));

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    private UUID activeEnrollment(UUID studentProfileId, UUID enrollmentCourseId, UUID enrollmentBranchId) {
        var enrollmentId = UUID.randomUUID();
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, enrollmentCourseId,
                        enrollmentBranchId, EnrollmentConstants.EnrollmentStatus.ACTIVE)));
        return enrollmentId;
    }

    private UUID friend() {
        return activeEnrollment(UUID.randomUUID(), courseId, branchId);
    }

    private void stubTuitionAndTier(String discountPercent) {
        when(coursesManagement.getCourseTuition(courseId))
                .thenReturn(Optional.of(new CoursesManagement.CourseTuitionResponse(courseId, TUITION, true)));
        when(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(anyInt()))
                .thenReturn(Optional.of(new ComboDiscountTier(2, new BigDecimal(discountPercent))));
        when(groups.saveAndFlush(any(GroupRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateGroupRegistrationRequest request(UUID... enrollmentIds) {
        return new CreateGroupRegistrationRequest(List.of(enrollmentIds));
    }

    @Test
    void discountsEveryMemberByTheTierForTheGroupSize() {
        stubTuitionAndTier("10");

        var response = useCase.execute(actorAccountId, branchId, request(friend(), friend()));

        assertThat(response.discountPercent()).isEqualByComparingTo("10");
        assertThat(response.members()).hasSize(2).allSatisfy(member -> {
            assertThat(member.originalTuitionFee()).isEqualByComparingTo(TUITION);
            assertThat(member.discountedTuitionFee()).isEqualByComparingTo("4050000");
        });
        verify(events).publishEvent(any(BillingEvents.GroupRegistrationCreated.class));
    }

    @Test
    void rejectsAGroupOfOneEvenWhenTheSameEnrollmentIsSentTwice() {
        var only = friend();

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(only, only)))
                .isInstanceOf(MinimumGroupSizeException.class);
    }

    @Test
    void rejectsMembersOfDifferentCourses() {
        var otherCourse = activeEnrollment(UUID.randomUUID(), UUID.randomUUID(), branchId);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(friend(), otherCourse)))
                .isInstanceOf(GroupMemberMismatchException.class);
    }

    @Test
    void rejectsMembersOfDifferentBranches() {
        var otherBranch = activeEnrollment(UUID.randomUUID(), courseId, UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(friend(), otherBranch)))
                .isInstanceOf(GroupMemberMismatchException.class);
    }

    /** Một học viên ghi danh hai lớp của cùng khoá không được đếm thành hai người. */
    @Test
    void rejectsTheSameStudentCountedTwice() {
        var student = UUID.randomUUID();
        var first = activeEnrollment(student, courseId, branchId);
        var second = activeEnrollment(student, courseId, branchId);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(first, second)))
                .isInstanceOf(GroupMemberMismatchException.class);
    }

    @Test
    void rejectsAnEnrollmentThatAlreadyHasADiscount() {
        var inCombo = friend();
        when(discounts.isDiscounted(inCombo)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(friend(), inCombo)))
                .isInstanceOf(EnrollmentAlreadyDiscountedException.class);
    }

    @Test
    void rejectsAnEnrollmentThatAlreadyHasALiveInvoice() {
        var invoiced = friend();
        when(invoices.countByEnrollmentIdAndStatusNot(invoiced, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(friend(), invoiced)))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
    }

    @Test
    void rejectsWhenNoTierCoversTheGroupSize() {
        when(coursesManagement.getCourseTuition(courseId))
                .thenReturn(Optional.of(new CoursesManagement.CourseTuitionResponse(courseId, TUITION, true)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, branchId, request(friend(), friend())))
                .isInstanceOf(ComboDiscountTierNotConfiguredException.class);
    }
}
