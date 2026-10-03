package com.eduerp.modules.billing;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module billing phát ra. */
public sealed class BillingException extends AppException
        permits InvoiceNotFoundException, PaymentNotFoundException, EnrollmentNotFoundException,
        EnrollmentNotActiveForBillingException, CourseNotFoundException, CourseTuitionNotConfiguredException,
        InstallmentLimitExceededException, InvoiceAmountExceedsTuitionException, InvoiceNotPayableException,
        InvalidPaymentAmountException, InvalidCallbackSignatureException, UnknownPaymentGatewayException,
        PaymentGatewayUnavailableException, InvoiceHasPendingPaymentException {

    protected BillingException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
