package com.eduerp.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gắn một request-id cho mỗi request: nhận lại từ header nếu caller (gateway, FE) đã có sẵn, không
 * thì tự sinh. Đưa vào MDC để mọi dòng log trong lúc xử lý request đều mang theo id này (xem
 * {@code logging.pattern.level} trong application.yml), và trả lại trong response header để log
 * phía client/gateway nối được với log phía server — không có việc này thì debug một request
 * xuyên nhiều module chỉ còn cách đoán theo mốc thời gian.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} để chạy trước cả chuỗi filter của Spring Security: một
 * request bị 401/403 vẫn phải có request-id trong log, không chỉ request xác thực thành công.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdFrom(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String requestIdFrom(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
    }
}
