package com.eduerp.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Review finding Important #3: mặc định Spring Boot giới hạn multipart 1MB - một hợp đồng scan
     * thường vượt 1MB, phải ra ProblemDetail 413 rõ ràng thay vì lỗi thô. */
    @Test
    void mapsMaxUploadSizeExceededToPayloadTooLargeProblemDetail() {
        var ex = new MaxUploadSizeExceededException(DataSize.ofMegabytes(10).toBytes());

        var response = handler.handleMaxUploadSizeExceededException(ex, new HttpHeaders(),
                HttpStatus.PAYLOAD_TOO_LARGE, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        var detail = (ProblemDetail) response.getBody();
        assertThat(detail.getProperties())
                .containsEntry(ProblemDetails.Properties.ERROR_CODE, ProblemDetails.ErrorCodes.FILE_TOO_LARGE);
    }

    /** @PreAuthorize từ chối phải ra 403 - trước đây lọt về /error rồi thành 401, frontend đăng xuất. */
    @Test
    void mapsAccessDeniedToForbiddenProblemDetail() {
        var detail = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(detail.getProperties())
                .containsEntry(ProblemDetails.Properties.ERROR_CODE, ProblemDetails.ErrorCodes.ACCESS_DENIED);
    }

    @Test
    void addsStatusNameAsErrorCodeToSpringBuiltProblemDetails() {
        var problem = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

        ProblemDetails.ensureErrorCode(problem);

        assertThat(problem.getProperties()).containsEntry(ProblemDetails.Properties.ERROR_CODE, "METHOD_NOT_ALLOWED");
    }
}
