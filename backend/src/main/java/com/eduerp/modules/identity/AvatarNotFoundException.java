package com.eduerp.modules.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class AvatarNotFoundException extends IdentityException {

    public AvatarNotFoundException(UUID accountId) {
        super(
                IdentityConstants.ErrorCodes.AVATAR_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Tài khoản chưa có ảnh đại diện: " + accountId);
    }
}
