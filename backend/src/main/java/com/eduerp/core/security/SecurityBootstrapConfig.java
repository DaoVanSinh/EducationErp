package com.eduerp.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Bật Spring Security một lần cho toàn app. Từng module tự khai {@code SecurityFilterChain} và
 * endpoint của mình (identity hiện là nơi duy nhất làm việc đó, vì nó sở hữu cookie/JWT filter) —
 * lớp này chỉ bật cơ chế, không biết path nào thuộc module nào.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityBootstrapConfig {

    /**
     * Nhận {@code PermissionEvaluator} qua interface của Spring Security, không import thẳng lớp
     * cài đặt cụ thể (hiện là {@code access.internal.permission.ScopedPermissionEvaluator}, ẩn
     * khỏi mọi module khác) — Spring tự nối theo kiểu, core không cần biết access tồn tại.
     */
    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator evaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }
}
