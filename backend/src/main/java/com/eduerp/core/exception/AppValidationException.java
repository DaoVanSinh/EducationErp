package com.eduerp.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 400 chung cho một trường cụ thể không hợp lệ theo nghiệp vụ (không phải lỗi format mà
 * {@code @Valid} đã bắt) - vd một UUID đúng định dạng nhưng không trỏ tới bản ghi nào còn tồn tại ở
 * module khác. Dùng chung thay vì mỗi module tự định nghĩa một lớp "ValidationException" trùng nhau.
 */
public final class AppValidationException extends AppException {

    public AppValidationException(String field, String message) {
        super("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, field + ": " + message);
    }
}
