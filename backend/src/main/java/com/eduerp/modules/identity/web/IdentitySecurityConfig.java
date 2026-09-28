package com.eduerp.modules.identity.web;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.JwtTokenService;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class IdentitySecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Nhận {@code PermissionEvaluator} qua interface của Spring Security, không import thẳng
     * {@code ScopedPermissionEvaluator} — lớp đó nằm trong {@code access.internal}, Spring Modulith
     * che khỏi identity. Spring tự nối bean bằng kiểu, nên biên module vẫn nguyên vẹn.
     */
    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator evaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            AccessManagement access, AccountRepository accounts) throws Exception {
        http
                // Token nằm trong cookie nên CSRF là rủi ro thật: chỉ miễn đúng các endpoint chưa có
                // phiên (chưa thể có CSRF token trong trang). Mọi hành động trên phiên đã đăng nhập -
                // logout, đổi mật khẩu, sửa profile - vẫn phải mang CSRF token.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh",
                                "/api/account/forgot-password", "/api/account/reset-password"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/account/forgot-password",
                                "/api/account/reset-password")
                        .permitAll()
                        .anyRequest().authenticated())
                // Mặc định Spring trả 403 cho request chưa xác thực; API này phải trả 401 để frontend
                // biết cần refresh phiên thay vì hiểu là thiếu quyền.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, access, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
