package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.EnrollmentBilledByComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Số tiền và hạn do {@code TuitionInvoiceIssuer} tính, phần đó có test riêng. Ở đây chỉ chốt các điều kiện chặn. */
class CreateInvoiceTest {

    private static final BigDecimal TUITION = new BigDecimal("12000000");

    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final EnrollmentDiscounts discounts = mock(EnrollmentDiscounts.class);
    private final TuitionInvoiceIssuer issuer = mock(TuitionInvoiceIssuer.class);
    private final CreateInvoice useCase = new CreateInvoice(enrollmentManagement, coursesManagement,
            mock(BillingBranchGuard.class), discounts, issuer,
            new InvoiceResponseAssembler(mock(ComboRepository.class), coursesManagement,
                    mock(StudentsManagement.class)));

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final CreateInvoiceRequest request = new CreateInvoiceRequest(enrollmentId);

    private EnrollmentManagement.EnrollmentSummaryResponse stubEnrollment(
            EnrollmentConstants.EnrollmentStatus status) {
        var enrollment = new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, courseId,
                branchId, status, UUID.randomUUID());
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(enrollment));
        return enrollment;
    }

    private void stubTuition(BigDecimal tuitionFee) {
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, tuitionFee, true)));
    }

    @Test
    void rejectsAnUnknownEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void rejectsAWithdrawnEnrollment() {
        stubEnrollment(EnrollmentConstants.EnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentNotActiveForBillingException.class);
    }

    @Test
    void rejectsAnEnrollmentBilledThroughACombo() {
        stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        when(discounts.isInCombo(enrollmentId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentBilledByComboException.class);
    }

    @Test
    void rejectsAnEnrollmentWhoseCourseIsGone() {
        stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void rejectsACourseWithoutATuitionFee() {
        stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubTuition(null);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(CourseTuitionNotConfiguredException.class);
    }

    @Test
    void reportsAlreadyInvoicedWhenNothingIsLeftToIssue() {
        var enrollment = stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubTuition(TUITION);
        when(issuer.issueFor(enrollment, actorAccountId, actorBranchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
    }

    /** Hai request cùng lập một lúc: chỉ mục duy nhất của database là lớp chặn cuối, không được rơi thành 500. */
    @Test
    void translatesAConcurrentIssueIntoAlreadyInvoiced() {
        var enrollment = stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubTuition(TUITION);
        when(issuer.issueFor(enrollment, actorAccountId, actorBranchId))
                .thenThrow(new DataIntegrityViolationException("uq_invoices_enrollment_installment"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
    }

    @Test
    void returnsTheIssuedInvoice() {
        var enrollment = stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubTuition(TUITION);
        var invoice = new Invoice(enrollmentId, studentProfileId, courseId, branchId, 1, TUITION,
                LocalDate.of(2026, 11, 30), actorAccountId);
        when(issuer.issueFor(any(), any(), any())).thenReturn(Optional.of(invoice));

        var response = useCase.execute(actorAccountId, actorBranchId, request);

        assertThat(response.amount()).isEqualByComparingTo(TUITION);
        assertThat(enrollment.enrollmentId()).isEqualTo(response.enrollmentId());
    }

    /** Thành viên nhóm luôn có giá đã chốt, kể cả khi khoá đã bị đổi giá về sau. */
    @Test
    void doesNotRequireListPriceForAGroupMember() {
        stubEnrollment(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        when(discounts.groupTuitionOf(enrollmentId)).thenReturn(Optional.of(new BigDecimal("9000000")));
        when(issuer.issueFor(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
    }
}
