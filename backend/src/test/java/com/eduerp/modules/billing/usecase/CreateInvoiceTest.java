package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class CreateInvoiceTest {

    private static final BigDecimal TUITION = new BigDecimal("12000000");
    private static final BigDecimal HALF = new BigDecimal("6000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 30);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateInvoice useCase =
            new CreateInvoice(invoices, enrollmentManagement, coursesManagement, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentBranchId = UUID.randomUUID();

    private CreateInvoiceRequest request(BigDecimal amount) {
        return new CreateInvoiceRequest(enrollmentId, amount, DUE_DATE);
    }

    private void stubActiveEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, courseId,
                        enrollmentBranchId, EnrollmentConstants.EnrollmentStatus.ACTIVE)));
    }

    private void stubTuition(BigDecimal tuitionFee) {
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, tuitionFee, true)));
    }

    private Invoice existingInvoice(int installmentNumber, BigDecimal amount) {
        return new Invoice(enrollmentId, studentProfileId, courseId, enrollmentBranchId, installmentNumber, amount,
                DUE_DATE, actorAccountId);
    }

    private void stubExistingInvoices(List<Invoice> existing) {
        when(invoices.countByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn((long) existing.size());
        when(invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(existing);
    }

    private void stubSaveEchoesBack() {
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    /** Rút khỏi lớp giữ nguyên hoá đơn cũ nhưng chặn phát hành đợt mới (spec mục 12). */
    @Test
    void rejectsAWithdrawnEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, courseId,
                        enrollmentBranchId, EnrollmentConstants.EnrollmentStatus.WITHDRAWN)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(EnrollmentNotActiveForBillingException.class);
    }

    @Test
    void rejectsAnEnrollmentWhoseCourseIsGone() {
        stubActiveEnrollment();
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void rejectsACourseWithoutATuitionFee() {
        stubActiveEnrollment();
        stubTuition(null);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(CourseTuitionNotConfiguredException.class);
    }

    @Test
    void numbersTheFirstInstallmentOneAndPublishesInvoiceCreated() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of());
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.installmentNumber()).isEqualTo(1);
        assertThat(response.enrollmentId()).isEqualTo(enrollmentId);
        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.branchId()).isEqualTo(enrollmentBranchId);
        assertThat(response.amount()).isEqualByComparingTo(HALF);
        assertThat(response.amountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        verify(events).publishEvent(any(BillingEvents.InvoiceCreated.class));
    }

    @Test
    void numbersTheSecondInstallmentTwo() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("3000000")))
                .installmentNumber()).isEqualTo(2);
    }

    /**
     * Review Focus #3: đợt 1 = 50%, đợt 2 = 50% thì đợt 3 với SỐ TIỀN DƯƠNG BẤT KỲ phải bị chặn vì
     * vượt học phí - lỗi đúng phải là INVOICE_AMOUNT_EXCEEDS_TUITION, KHÔNG phải
     * INSTALLMENT_LIMIT_EXCEEDED (hạn mức 3 đợt chưa chạm, mới có 2 đợt).
     */
    @Test
    void rejectsAThirdInstallmentOfAnyAmountOnceTheTuitionIsFullyInvoiced() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF), existingInvoice(2, HALF)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1"))))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class)
                .hasMessageContaining("12000000");
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1000000"))))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class);
    }

    /** Ranh giới: tổng đúng bằng học phí vẫn phải được phát hành, chỉ vượt mới bị chặn. */
    @Test
    void acceptsAnInstallmentThatExactlyCompletesTheTuition() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.amount()).isEqualByComparingTo(HALF);
    }

    /** Hạn mức 3 đợt là một lỗi RIÊNG, chỉ gặp khi tổng tiền còn chỗ mà số đợt đã hết (ví dụ 3 đợt
     * nhỏ chưa dùng hết học phí) - nếu không tách, người dùng nhận thông báo sai nguyên nhân. */
    @Test
    void rejectsAFourthInstallmentEvenWhenTuitionBudgetRemains() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, new BigDecimal("1000000")),
                existingInvoice(2, new BigDecimal("1000000")), existingInvoice(3, new BigDecimal("1000000"))));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1000000"))))
                .isInstanceOf(InstallmentLimitExceededException.class)
                .hasMessageContaining("3");
    }

    /**
     * Final review Important #3: khi race condition giữa hai request đồng thời vẫn lọt qua được các
     * kiểm tra đếm/tổng ở trên (cả hai đọc cùng một trạng thái trước khi ai kịp ghi), lớp phòng thủ DB
     * (migration V20, partial unique index) là thứ chặn một trong hai ở bước lưu - usecase phải dịch
     * {@code DataIntegrityViolationException} đó sang đúng lỗi nghiệp vụ, không để lọt ra thành 500.
     */
    @Test
    void translatesADatabaseRaceOnTheInstallmentNumberIntoAnInstallmentLimitExceededException() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of());
        when(invoices.saveAndFlush(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException("uq_invoices_enrollment_installment"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(InstallmentLimitExceededException.class);
    }

    /** Hoá đơn đã huỷ không chiếm chỗ trong 3 đợt và không tính vào tổng (spec mục 5). */
    @Test
    void ignoresCancelledInvoicesInBothTheCountAndTheSum() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        // Repository đã loại CANCELLED, nên usecase chỉ thấy 1 hoá đơn còn sống.
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.installmentNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
    }
}
