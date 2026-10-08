package com.eduerp.modules.billing.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ba endpoint public của billing, khai bằng một {@code SecurityFilterChain} RIÊNG của module - đúng
 * thiết kế mà {@code core.security.SecurityBootstrapConfig} mô tả ("từng module tự khai
 * SecurityFilterChain và endpoint của mình"). KHÔNG thêm path vào
 * {@code identity.web.IdentitySecurityConfig}: {@code identity} đọc {@code billing} sẽ sinh vòng
 * {@code identity → billing → courses → identity} và {@code ApplicationModules.verify()} fail.
 *
 * <p>{@code @Order(1)} để chain này được xét TRƯỚC chain của identity (chain đó không có
 * {@code securityMatcher} nên là catch-all và nhận {@code LOWEST_PRECEDENCE}).
 *
 * <p>CSRF tắt trên đúng ba path này: MoMo gọi IPN bằng POST server-to-server, không thể mang CSRF
 * token của SPA. Cũng không gắn {@code CookieAuthenticationFilter} - ba endpoint này không cần biết
 * "ai đang gọi", xác thực đi bằng chữ ký HMAC trong usecase.
 */
@Configuration
class BillingSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain billingPublicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(BillingPublicPaths.ALL)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
