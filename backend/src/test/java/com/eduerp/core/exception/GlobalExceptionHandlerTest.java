package com.eduerp.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Review finding Important #3: mặc định Spring Boot giới hạn multipart 1MB, không có handler cho
 * MaxUploadSizeExceededException - lỗi hiện ra là 500 thô (không có ExceptionHandler nào bắt được)
 * thay vì ProblemDetail 413 rõ ràng. Một hợp đồng scan thường vượt 1MB. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMaxUploadSizeExceededToPayloadTooLargeProblemDetail() {
        var ex = new MaxUploadSizeExceededException(DataSize.ofMegabytes(10).toBytes());

        var detail = handler.handleMaxUploadSizeExceeded(ex);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(detail.getProperties()).containsEntry("errorCode", "FILE_TOO_LARGE");
    }
}
