package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Nhóm một người không phải nhóm - đó chỉ là một ghi danh, dùng luồng hoá đơn thường. */
public final class MinimumGroupSizeException extends BillingException {
    public MinimumGroupSizeException(int minimumMembers) {
        super("BILLING_GROUP_MINIMUM_SIZE", HttpStatus.BAD_REQUEST,
                "Đăng ký nhóm phải gồm ít nhất " + minimumMembers + " học viên khác nhau");
    }
}
