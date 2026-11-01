package com.eduerp.modules.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module identity phát ra. */
public sealed class IdentityException extends AppException
        permits AccountNotFoundException, InvalidCredentialsException, ReferenceNotFoundException, TokenInvalidException,
        EmailAlreadyExistsException, AccountInviteExpiredException, AccountAlreadyActivatedException,
        InvalidAvatarException, AvatarNotFoundException,
        IdentityException.CannotModifyOwnAccount, IdentityException.AccountNeverActivated {

    protected IdentityException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    /** Tự khoá chính mình là mất quyền quản trị ngay lập tức - chặn ở backend, không chỉ ẩn nút. */
    public static final class CannotModifyOwnAccount extends IdentityException {
        public CannotModifyOwnAccount() {
            super(IdentityConstants.ErrorCodes.SELF_MODIFICATION_FORBIDDEN, HttpStatus.CONFLICT,
                    "Không thể khoá chính tài khoản đang đăng nhập");
        }
    }

    /** Tài khoản chưa từng kích hoạt thì mở lại bằng "Gửi lại lời mời" (sinh mật khẩu tạm mới), không phải mở khoá. */
    public static final class AccountNeverActivated extends IdentityException {
        public AccountNeverActivated(java.util.UUID accountId) {
            super(IdentityConstants.ErrorCodes.ACCOUNT_NEVER_ACTIVATED, HttpStatus.CONFLICT,
                    "Tài khoản " + accountId + " chưa từng kích hoạt - hãy dùng Gửi lại lời mời");
        }
    }
}
