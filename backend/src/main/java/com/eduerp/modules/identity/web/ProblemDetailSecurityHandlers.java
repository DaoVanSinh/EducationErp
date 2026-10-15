package com.eduerp.modules.identity.web;

import com.eduerp.core.exception.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Lỗi bị chặn ở tầng filter (chưa tới controller, nên {@code GlobalExceptionHandler} không bắt
 * được) — vd thiếu CSRF token, chưa đăng nhập. Ghi thẳng ProblemDetail ra response thay vì để
 * Spring {@code sendError} rồi dispatch về {@code /error}: lượt đó không mang cookie xác thực nên
 * 403 thiếu CSRF bị đổi thành 401, frontend tưởng hết phiên và đăng xuất người dùng.
 */
final class ProblemDetailSecurityHandlers {

    private ProblemDetailSecurityHandlers() {
    }

    static AuthenticationEntryPoint unauthenticated(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, ProblemDetails.of(HttpStatus.UNAUTHORIZED,
                ProblemDetails.ErrorCodes.UNAUTHENTICATED, "Bạn cần đăng nhập để tiếp tục"));
    }

    static AccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, ProblemDetails.of(HttpStatus.FORBIDDEN,
                ProblemDetails.ErrorCodes.ACCESS_DENIED, "Bạn không có quyền thực hiện thao tác này"));
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
