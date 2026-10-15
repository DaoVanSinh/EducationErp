package com.eduerp.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Một chỗ duy nhất dựng {@link ProblemDetail} có {@code errorCode} — dùng chung cho
 * {@link GlobalExceptionHandler} (lỗi trong controller) và các handler của tầng security (lỗi bị
 * chặn ở filter, trước khi tới controller), để mọi lỗi ra client cùng một hình dạng (rule #9).
 */
public final class ProblemDetails {

    /** Tên field mở rộng trên ProblemDetail mà frontend ({@code ApiError.fromResponse}) đọc. */
    public static final class Properties {
        public static final String ERROR_CODE = "errorCode";
        public static final String FIELD_ERRORS = "errors";

        private Properties() {
        }
    }

    /** Mã lỗi ổn định cho các lỗi không thuộc nghiệp vụ module nào (cơ chế HTTP/bảo mật). */
    public static final class ErrorCodes {
        public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
        public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
        public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
        public static final String ACCESS_DENIED = "ACCESS_DENIED";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

        private ErrorCodes() {
        }
    }

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(errorCode);
        problem.setDetail(detail);
        problem.setProperty(Properties.ERROR_CODE, errorCode);
        return problem;
    }

    /**
     * Gắn {@code errorCode} cho ProblemDetail Spring tự dựng (400/404/405/415...) — Spring không có
     * khái niệm errorCode, nên lấy tên chuẩn của HTTP status ({@code NOT_FOUND},
     * {@code METHOD_NOT_ALLOWED}...) làm mã ổn định. Giữ nguyên nếu đã có sẵn.
     */
    public static void ensureErrorCode(ProblemDetail problem) {
        if (problem.getProperties() != null && problem.getProperties().containsKey(Properties.ERROR_CODE)) {
            return;
        }
        String errorCode = statusName(problem.getStatus());
        problem.setTitle(errorCode);
        problem.setProperty(Properties.ERROR_CODE, errorCode);
    }

    private static String statusName(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.name() : String.valueOf(status);
    }
}
