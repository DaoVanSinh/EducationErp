package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.math.BigDecimal;
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
}
