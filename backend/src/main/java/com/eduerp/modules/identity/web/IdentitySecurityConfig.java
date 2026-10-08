package com.eduerp.modules.identity.web;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.JwtTokenService;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * {@code @EnableWebSecurity}/{@code @EnableMethodSecurity} và việc bật cơ chế đã chuyển sang
 * {@code core.security.SecurityBootstrapConfig} — lớp này chỉ còn khai cụ thể endpoint nào của
 * identity/module khác cần quyền gì, vì đó là quyết định nghiệp vụ (rule #4), không phải cơ chế.
 */
@Configuration
class IdentitySecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/complete-invite", "/api/auth/refresh",
                                "/api/account/forgot-password", "/api/account/reset-password"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/complete-invite", "/api/auth/refresh",
                                "/api/account/forgot-password", "/api/account/reset-password")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, access, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
