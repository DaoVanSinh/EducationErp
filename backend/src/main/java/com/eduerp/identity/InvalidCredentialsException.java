package com.eduerp.identity;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        super("IDENTITY_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    }
}
