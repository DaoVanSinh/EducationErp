package com.eduerp.identity.web;

import com.eduerp.identity.internal.permission.PermissionCacheService;
import com.eduerp.identity.internal.permission.ScopedPermissionEvaluator;
import com.eduerp.identity.internal.repository.AccountRepository;
import com.eduerp.identity.internal.token.JwtTokenService;
import com.eduerp.identity.internal.token.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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

    @Bean
    DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(ScopedPermissionEvaluator evaluator) {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            PermissionCacheService permissionCache, AccountRepository accounts) throws Exception {
        http
                // Token nằm trong cookie nên CSRF là rủi ro thật: chỉ miễn đúng các endpoint chưa có
                // phiên (chưa thể có CSRF token trong trang). Mọi hành động trên phiên đã đăng nhập -
                // logout, đổi mật khẩu, sửa profile - vẫn phải mang CSRF token.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
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
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, permissionCache, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
