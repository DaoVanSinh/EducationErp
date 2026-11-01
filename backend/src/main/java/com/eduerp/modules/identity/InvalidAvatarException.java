package com.eduerp.modules.identity;

import org.springframework.http.HttpStatus;

public final class InvalidAvatarException extends IdentityException {

    public InvalidAvatarException() {
        super(
                IdentityConstants.ErrorCodes.INVALID_AVATAR,
                HttpStatus.BAD_REQUEST,
                "Chọn ảnh PNG, JPEG hoặc WebP tối đa 5 MB và 4 triệu điểm ảnh.");
    }
}
