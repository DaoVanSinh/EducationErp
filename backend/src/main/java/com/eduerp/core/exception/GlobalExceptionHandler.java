package com.eduerp.core.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(ex.getStatus());
        detail.setTitle(ex.getErrorCode());
        detail.setDetail(ex.getMessage());
        detail.setProperty("errorCode", ex.getErrorCode());
        return detail;
    }

    /** File hợp đồng scan thường vượt mặc định 1MB của Spring Boot - không có handler này thì
     * MaxUploadSizeExceededException lọt ra ngoài thành 500 thô thay vì ProblemDetail rõ ràng. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        detail.setTitle("FILE_TOO_LARGE");
        detail.setDetail("File vượt quá dung lượng cho phép");
        detail.setProperty("errorCode", "FILE_TOO_LARGE");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("VALIDATION_FAILED");
        detail.setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList());
        return detail;
    }
}
