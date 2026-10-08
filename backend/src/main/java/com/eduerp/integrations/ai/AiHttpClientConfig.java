package com.eduerp.integrations.ai;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Giới hạn thời gian cho lệnh gọi nhà cung cấp mô hình.
 *
 * <p>{@code AnthropicChatAutoConfiguration} của Spring AI 1.0.9 nhận
 * {@code ObjectProvider<RestClient.Builder>} và dùng chính builder tự cấu hình của Boot, mà bản 1.0.9
 * KHÔNG có property timeout nào ({@code AnthropicConnectionProperties} chỉ có api-key, base-url,
 * completions-path, version, beta-version - đã đọc source). Vì vậy một
 * {@code RestClientCustomizer} là cơ chế duy nhất đặt được timeout cho đường này mà không phải viết
 * lại cả autoconfiguration.
 *
 * <p>Customizer này là TOÀN CỤC, và đó là lý do Task 5 đã tháo customizer toàn cục của
 * {@code integrations.payment} trước: sau Phân hệ 5, người tiêu thụ duy nhất còn lại của
 * {@code RestClient.Builder} tự cấu hình là {@code AnthropicApi} của Spring AI
 * ({@code MomoPaymentGatewayClient} tự ghi đè request factory của chính nó). Nếu một integration thứ
 * ba sau này cũng tiêm {@code RestClient.Builder}, nó phải tự ghi đè request factory như MoMo đang
 * làm, chứ không thêm một customizer toàn cục thứ hai.
 *
 * <p>60 giây: một câu trả lời của model chat bình thường mất 15-30 giây, nên 10 giây (giới hạn của
 * cổng thanh toán) sẽ cắt ngang mọi lệnh gọi. Vẫn có trần vì lệnh gọi này nằm trong một request HTTP
 * đồng bộ của người dùng - cộng với {@code spring.ai.retry.max-attempts: 2}, trần xấu nhất là hai
 * lượt chứ không phải mười.
 */
@Configuration
class AiHttpClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    RestClientCustomizer aiChatTimeoutCustomizer() {
        var requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(CONNECT_TIMEOUT)
                        .withReadTimeout(READ_TIMEOUT));
        return builder -> builder.requestFactory(requestFactory);
    }
}
