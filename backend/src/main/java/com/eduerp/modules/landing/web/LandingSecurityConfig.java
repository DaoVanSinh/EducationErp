package com.eduerp.modules.landing.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Endpoint công khai của module landing, khai bằng một {@code SecurityFilterChain} riêng (mirror
 * {@code BillingSecurityConfig}) để khách truy cập Landing Page chưa đăng nhập vẫn đọc được dữ liệu
 * khoá học, lớp học, chi nhánh và giảng viên từ ERP mà không sinh vòng phụ thuộc với
 * {@code modules.identity}.
 */
@Configuration
class LandingSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain landingPublicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(LandingPublicPaths.ALL)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
