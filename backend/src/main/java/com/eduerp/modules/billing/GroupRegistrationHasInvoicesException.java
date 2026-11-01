package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Huỷ nhóm là xoá cứng và trả trần học phí về giá niêm yết - chỉ an toàn khi chưa ai có hoá đơn sống. */
public final class GroupRegistrationHasInvoicesException extends BillingException {
    public GroupRegistrationHasInvoicesException(UUID groupId) {
        super("BILLING_GROUP_HAS_INVOICES", HttpStatus.CONFLICT,
                "Nhóm " + groupId + " đã có thành viên được phát hành hoá đơn, không huỷ được");
    }
}
