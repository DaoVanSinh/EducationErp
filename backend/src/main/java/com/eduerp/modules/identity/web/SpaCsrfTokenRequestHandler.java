package com.eduerp.modules.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Cầu nối giữa cơ chế CSRF mặc định của Spring Security và một SPA đọc token từ cookie.
 *
 * <p>Mặc định Spring che token bằng XOR để chống BREACH, nên giá trị hợp lệ chỉ tồn tại trong trang
 * do server render. SPA thì không có trang đó: nó chỉ đọc được cookie {@code XSRF-TOKEN} - giá trị
 * thô, chưa che. Vì vậy khi client gửi token qua header thì so khớp thô, còn qua tham số form thì
 * vẫn giữ đường XOR cho những chỗ render phía server.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.masked.handle(request, response, csrfToken);
        // Spring chỉ ghi cookie khi có ai đó thực sự đọc token, mà một SPA thì không đọc qua request
        // attribute. Gọi get() ở đây để mọi phản hồi - kể cả GET - đều mang cookie về cho client.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
                ? this.plain.resolveCsrfTokenValue(request, csrfToken)
                : this.masked.resolveCsrfTokenValue(request, csrfToken);
    }
}
