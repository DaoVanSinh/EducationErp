package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Ghi danh đã thu tiền, đang có giao dịch chờ, hoặc đã có đủ hoá đơn thì hệ thống không tự đổi hoá đơn được: làm
 * vậy sẽ lệch với số tiền đã thu. Hoá đơn chưa thu đồng nào thì được huỷ và lập lại tự động, không rơi vào lỗi này.
 */
public final class EnrollmentAlreadyInvoicedException extends BillingException {
    public EnrollmentAlreadyInvoicedException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_ALREADY_INVOICED", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " đã thu tiền hoặc đã có đủ hoá đơn, không lập lại hoá đơn được");
    }
}
