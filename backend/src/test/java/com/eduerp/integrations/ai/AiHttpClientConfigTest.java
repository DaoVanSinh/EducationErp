package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Hai hằng dưới đây là thứ ngăn một lệnh gọi treo tới Anthropic giữ một thread Tomcat vô thời hạn, và
 * đồng thời ngăn một read timeout quá ngắn cắt ngang một câu trả lời bình thường. Đó là lý do chúng
 * được chốt bằng test thay vì để trong một file cấu hình ai cũng sửa được không ai thấy.
 */
class AiHttpClientConfigTest {

    @Test
    void theReadTimeoutIsLongEnoughForAChatAnswerButStillBounded() {
        assertThat(AiHttpClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
        assertThat(AiHttpClientConfig.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(60));
        assertThat(AiHttpClientConfig.READ_TIMEOUT)
                .as("10 giây của cổng thanh toán quá ngắn cho một model chat - đó là lý do customizer "
                        + "toàn cục của payment đã bị tháo ở Task 5")
                .isGreaterThan(Duration.ofSeconds(30));
    }

    @Test
    void theCustomizerAppliesACleanlyBuildableRequestFactoryToAnyBuilder() {
        var customizer = new AiHttpClientConfig().aiChatTimeoutCustomizer();
        var builder = RestClient.builder();

        assertThatCode(() -> customizer.customize(builder)).doesNotThrowAnyException();
        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
