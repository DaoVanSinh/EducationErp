package com.eduerp.core.exception;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Kế thừa {@link ResponseEntityExceptionHandler} để MỌI lỗi có sẵn của Spring MVC (JSON hỏng, sai
 * kiểu tham số, sai method, route không tồn tại...) cũng ra ProblemDetail ngay trong controller.
 * Trước đây chỉ bắt vài loại: phần còn lại rơi về {@code /error}, mà lượt dispatch ERROR không mang
 * cookie xác thực nên bị chặn thành 401 — frontend hiểu 401 là hết phiên và đá người dùng ra ngoài
 * chỉ vì một request sai, hoặc vì bấm vào chỗ không có quyền (403 từ {@code @PreAuthorize}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex) {
        return ProblemDetails.of(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    /** {@code @PreAuthorize} từ chối: phải là 403 (đã đăng nhập, thiếu quyền), không phải 401. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ProblemDetails.ErrorCodes.ACCESS_DENIED,
                "Bạn không có quyền thực hiện thao tác này");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, ProblemDetails.ErrorCodes.UNAUTHENTICATED,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }

    /** Lưới cuối: lỗi bất ngờ ra 500 có log, không lọt về {@code /error} rồi biến thành 401. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ProblemDetails.ErrorCodes.INTERNAL_ERROR,
                "Hệ thống gặp lỗi, vui lòng thử lại sau");
    }

    /** File hợp đồng scan thường vượt mặc định 1MB của Spring Boot - trả 413 rõ ràng thay vì lỗi thô. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.PAYLOAD_TOO_LARGE,
                ProblemDetails.ErrorCodes.FILE_TOO_LARGE, "File vượt quá dung lượng cho phép");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST,
                ProblemDetails.ErrorCodes.VALIDATION_FAILED, null);
        problem.setProperty(ProblemDetails.Properties.FIELD_ERRORS, ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList());
        return ResponseEntity.badRequest().body(problem);
    }

    /** Mọi ProblemDetail do Spring tự dựng đều đi qua đây — gắn thêm errorCode cho đồng bộ. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            ProblemDetails.ensureErrorCode(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
