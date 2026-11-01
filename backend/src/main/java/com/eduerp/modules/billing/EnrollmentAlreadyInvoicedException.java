package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Ghi danh đã có hoá đơn sống tính theo giá niêm yết thì không lập nhóm được nữa: hạ trần học phí lúc này
 * sẽ làm tổng hoá đơn đã phát hành vượt trần mới. Huỷ hoá đơn trước rồi lập nhóm.
 */
public final class EnrollmentAlreadyInvoicedException extends BillingException {
    public EnrollmentAlreadyInvoicedException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_ALREADY_INVOICED", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " đã có hoá đơn, huỷ hoá đơn trước khi lập nhóm");
    }
}
