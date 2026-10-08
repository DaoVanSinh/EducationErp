package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Khẳng định quan trọng nhất của task này KHÔNG phải "gọi được Anthropic" (không thể, chưa có khoá
 * thật - Global Constraint) mà là "thêm starter vào KHÔNG làm chết context của toàn bộ 60+ IT đang
 * có". Khoá rỗng là trạng thái mặc định của repo và của CI, nên nó phải là một trạng thái khởi động
 * được; chỉ lệnh gọi thật mới được phép thất bại.
 */
@Testcontainers
@SpringBootTest
class AnthropicStarterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    ChatModel chatModel;

    @Value("${spring.ai.anthropic.api-key}")
    String apiKey;

    @Value("${spring.ai.anthropic.chat.options.model}")
    String configuredModel;

    /**
     * Khẳng định đúng là "không có khoá thật nào bị commit" - không phải "biến môi trường của máy
     * chạy test đang trống". Final review: một dev làm đúng việc duy nhất spec mục 9.1 yêu cầu
     * (export ANTHROPIC_API_KEY thật rồi chạy lại suite) sẽ khiến bản assert cũ (isEmpty()) đỏ sai -
     * oan cho một thứ đang hoạt động đúng. Giá trị resolve được phải là RỖNG (CI, máy chưa cấu hình)
     * HOẶC đúng bằng biến môi trường thật (máy đã cấu hình) - không bao giờ là một chuỗi khác hai
     * khả năng đó, vì khi đó mới là một khoá bị hardcode.
     */
    @Test
    void theContextStartsAndExposesAChatModelEvenWithAnEmptyApiKey() {
        var realEnvKey = System.getenv("ANTHROPIC_API_KEY");
        assertThat(apiKey).as("phải RỖNG hoặc đúng bằng biến môi trường thật - không bao giờ khác")
                .isEqualTo(realEnvKey == null ? "" : realEnvKey);
        assertThat(chatModel).isNotNull();
    }

    /**
     * Ruling R2 chốt model mặc định; đổi nó là một quyết định về chi phí/chất lượng, không phải một
     * lần sửa nhỏ, nên nó được chốt bằng test.
     *
     * <p>Final review Critical #1: model ban đầu "claude-sonnet-5" không tương thích với Spring AI
     * 1.0.9 - {@code AnthropicChatProperties} (đã decompile) LUÔN gán cứng
     * {@code AnthropicChatModel.DEFAULT_TEMPERATURE} cho field {@code temperature}, và theo catalog
     * model hiện tại, Claude Sonnet 5 "non-default sampling parameters rejected" - mọi lệnh gọi thật
     * sẽ nhận 400 ngay cả khi khoá đúng, lộ ra thành 503 CHAT_PROVIDER_UNAVAILABLE không thể phân biệt
     * với "chưa cấu hình khoá". Đổi sang Haiku 4.5 - vừa tránh hẳn lớp tương thích sampling-params của
     * dòng model 4.7+/5.x (catalog không liệt kê giới hạn sampling nào cho Haiku 4.5), vừa đúng tầm
     * một chatbot tư vấn-truy xuất đơn giản ("Fastest and most cost-effective model for simple
     * tasks").
     */
    @Test
    void theDefaultModelIsTheOneRulingR2Chose() {
        assertThat(configuredModel).isEqualTo("claude-haiku-4-5-20251001");
    }
}
