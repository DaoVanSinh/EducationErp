package com.eduerp.identity;

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
class SecurityConfig {

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
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password",
                                "/api/auth/reset-password")
                        .permitAll()
                        .anyRequest().authenticated())
                // Không cấu hình formLogin/httpBasic (auth là cookie-based, không phải form/basic),
                // nên phải khai báo rõ entry point 401 - nếu không Spring Security sẽ mặc định
                // dùng Http403ForbiddenEntryPoint và trả 403 thay vì 401 cho request chưa xác thực.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new CookieAuthenticationFilter(jwtTokenService, blacklist, permissionCache, accounts),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
