package com.eduerp.modules.identity.web;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.JwtTokenService;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
    CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:*}") String allowedOrigins) {
        var config = new CorsConfiguration();
        var patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(patterns.isEmpty() ? List.of("*") : patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-CSRF-Token", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            AccessManagement access, AccountRepository accounts, ObjectMapper objectMapper) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Token nằm trong cookie nên CSRF là rủi ro thật: chỉ miễn đúng các endpoint chưa có
                // phiên (chưa thể có CSRF token trong trang). Mọi hành động trên phiên đã đăng nhập -
                // logout, đổi mật khẩu, sửa profile - vẫn phải mang CSRF token.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/complete-invite", "/api/auth/refresh",
                                "/api/account/forgot-password", "/api/account/reset-password"))
                .authorizeHttpRequests(auth -> auth
                        // Lượt dispatch ERROR không mang cookie xác thực: chặn nó thì mọi lỗi lọt về
                        // /error (400/404/500...) đều bị đổi thành 401 - lưới an toàn sau GlobalExceptionHandler.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/complete-invite", "/api/auth/refresh",
                                "/api/account/forgot-password", "/api/account/reset-password")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ProblemDetailSecurityHandlers.unauthenticated(objectMapper))
                        .accessDeniedHandler(ProblemDetailSecurityHandlers.accessDenied(objectMapper)))
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, access, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
