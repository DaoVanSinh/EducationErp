package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BillingExceptionTest {

    @Test
    void invoiceNotFoundIsNotFound() {
        var ex = new InvoiceNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void paymentNotFoundIsNotFound() {
        var ex = new PaymentNotFoundException("order-1");
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_PAYMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void enrollmentNotFoundIsBadRequest() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enrollmentNotActiveForBillingIsConflict() {
        var ex = new EnrollmentNotActiveForBillingException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void courseNotFoundIsBadRequest() {
        var ex = new CourseNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COURSE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void courseTuitionNotConfiguredIsConflict() {
        var ex = new CourseTuitionNotConfiguredException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COURSE_TUITION_NOT_CONFIGURED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void installmentLimitExceededIsConflictAndNamesTheLimit() {
        var ex = new InstallmentLimitExceededException(UUID.randomUUID(), 3);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INSTALLMENT_LIMIT_EXCEEDED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("3");
    }

    @Test
    void invoiceAmountExceedsTuitionIsConflictAndNamesBothNumbers() {
        var ex = new InvoiceAmountExceedsTuitionException(new BigDecimal("13000000"), new BigDecimal("12000000"));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("13000000", "12000000");
    }

    @Test
    void invoiceNotPayableIsConflict() {
        var ex = new InvoiceNotPayableException(UUID.randomUUID(), BillingConstants.InvoiceStatus.PAID);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_NOT_PAYABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("PAID");
    }

    @Test
    void invalidPaymentAmountIsBadRequest() {
        var ex = new InvalidPaymentAmountException(new BigDecimal("-1"));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVALID_PAYMENT_AMOUNT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Message không được chứa orderId - Review Focus #5: không để response/log thành oracle. */
    @Test
    void invalidCallbackSignatureIsBadRequestAndNamesOnlyTheGateway() {
        var ex = new InvalidCallbackSignatureException(PaymentGatewayType.MOMO);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVALID_CALLBACK_SIGNATURE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("MOMO").doesNotContain("order");
    }

    @Test
    void unknownPaymentGatewayIsBadRequest() {
        var ex = new UnknownPaymentGatewayException(BillingConstants.PaymentMethod.MANUAL);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_UNKNOWN_PAYMENT_GATEWAY");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("MANUAL");
    }

    @Test
    void minimumComboSizeIsBadRequestAndNamesTheMinimum() {
        var ex = new MinimumComboSizeException(2);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_MINIMUM_SIZE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("2");
    }

    @Test
    void studentMismatchInComboIsBadRequestAndNamesTheOffendingEnrollment() {
        var enrollmentId = UUID.randomUUID();
        var ex = new StudentMismatchInComboException(enrollmentId);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_STUDENT_MISMATCH");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains(enrollmentId.toString());
    }

    @Test
    void enrollmentAlreadyInComboIsConflict() {
        var ex = new EnrollmentAlreadyInComboException(List.of(UUID.randomUUID()));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_ALREADY_IN_COMBO");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Mirror CourseTuitionNotConfiguredException: giá chưa cấu hình thì chặn, không suy đoán 0%. */
    @Test
    void comboDiscountTierNotConfiguredIsConflictAndNamesTheCourseCount() {
        var ex = new ComboDiscountTierNotConfiguredException(2);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("2");
    }

    @Test
    void comboDiscountTierNotFoundIsNotFound() {
        var ex = new ComboDiscountTierNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void comboDiscountTierAlreadyExistsIsConflictAndNamesTheTier() {
        var ex = new ComboDiscountTierAlreadyExistsException(3);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("3");
    }

    @Test
    void comboNotFoundIsNotFound() {
        var ex = new ComboNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Review Focus #6: combo đã có chứng từ tài chính thì không xoá cứng được nữa. */
    @Test
    void comboHasInvoicesIsConflict() {
        var ex = new ComboHasInvoicesException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_HAS_INVOICES");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Spec mục 6: combo dùng LẠI đúng hai exception này (cùng type, cùng errorCode) - chỉ câu chữ
     * của message đổi theo đơn vị neo, để kế toán không đọc thấy chữ "Ghi danh" trên màn hình combo. */
    @Test
    void comboVariantsKeepTheSameErrorCodeButNameTheComboInTheMessage() {
        var comboId = UUID.randomUUID();
        var limit = InstallmentLimitExceededException.forCombo(comboId, 3);
        assertThat(limit.getErrorCode()).isEqualTo("BILLING_INSTALLMENT_LIMIT_EXCEEDED");
        assertThat(limit.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(limit.getMessage()).contains("Combo", comboId.toString(), "3").doesNotContain("Ghi danh");

        var exceeds = InvoiceAmountExceedsTuitionException.forCombo(new BigDecimal("18000000"),
                new BigDecimal("17850000"));
        assertThat(exceeds.getErrorCode()).isEqualTo("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
        assertThat(exceeds.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exceeds.getMessage()).contains("18000000", "17850000", "combo");
    }
}
