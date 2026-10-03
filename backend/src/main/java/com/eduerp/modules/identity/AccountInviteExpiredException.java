package com.eduerp.modules.identity;

import org.springframework.http.HttpStatus;

/** Không nhận accountId trong constructor — giống InvalidCredentialsException, không tiết lộ thêm gì. */
public final class AccountInviteExpiredException extends IdentityException {
    public AccountInviteExpiredException() {
        super("IDENTITY_INVITE_EXPIRED", HttpStatus.UNAUTHORIZED, "Lời mời đã hết hạn, vui lòng liên hệ quản trị viên");
    }
}
