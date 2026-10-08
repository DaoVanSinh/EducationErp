package com.eduerp.integrations.payment;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Giới hạn thời gian của riêng đường gọi cổng thanh toán.
 *
 * <p>Final review Important #4 của Phân hệ 3: trước bản vá, không timeout nào được cấu hình - một
 * kết nối treo tới MoMo giữ một connection HikariCP vô thời hạn, vì lệnh gọi này nằm trong một
 * usecase {@code @Transactional} ({@code InitiateOnlinePayment}).
 *
 * <p>Phân hệ 5 đổi cơ chế áp dụng: bản vá cũ dùng một {@code @Bean RestClientCustomizer}, và Boot áp
 * customizer cho MỌI {@code RestClient.Builder} tự cấu hình. Khi đó giả định ghi trong javadoc cũ
 * ("MomoPaymentGatewayClient là nơi DUY NHẤT trong app tiêm RestClient.Builder") còn đúng. Spring AI
 * 1.0.9 phá giả định đó: {@code AnthropicChatAutoConfiguration} nhận
 * {@code ObjectProvider<RestClient.Builder>} và dùng chính builder của Boot, nên read timeout 10
 * giây hợp lý cho một API thanh toán sẽ cắt ngang mọi lệnh gọi model chat - thứ bình thường mất
 * 15-30 giây. Vì vậy request factory giờ được áp tại đúng nơi dùng nó
 * ({@code MomoPaymentGatewayClient}) và không còn bean toàn cục nào ở đây.
 */
final class PaymentHttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private PaymentHttpClientConfig() {
    }

    static ClientHttpRequestFactory timeoutRequestFactory() {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT));
    }
}
