package com.eduerp.integrations.payment;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Final review Important #4: {@code MomoPaymentGatewayClient} là nơi DUY NHẤT trong app tiêm
 * {@code RestClient.Builder} (đã xác nhận bằng grep), nên một {@code RestClientCustomizer} ở đây áp
 * dụng đúng và chỉ cho client đó - không có consumer nào khác bị ảnh hưởng. Trước bản vá, không
 * timeout nào được cấu hình: một kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn, vì
 * lệnh gọi này nằm trong một usecase {@code @Transactional} ({@code InitiateOnlinePayment}).
 */
@Configuration
class PaymentHttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    RestClientCustomizer paymentGatewayTimeoutCustomizer() {
        var requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT));
        return builder -> builder.requestFactory(requestFactory);
    }
}
