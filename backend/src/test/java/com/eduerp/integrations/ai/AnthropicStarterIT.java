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

    @Test
    void theContextStartsAndExposesAChatModelEvenWithAnEmptyApiKey() {
        assertThat(apiKey).as("mặc định phải RỖNG - khoá thật chỉ đến từ biến môi trường").isEmpty();
        assertThat(chatModel).isNotNull();
    }

    /** Ruling R2 chốt model mặc định; đổi nó là một quyết định về chi phí/chất lượng, không phải một
     * lần sửa nhỏ, nên nó được chốt bằng test. */
    @Test
    void theDefaultModelIsTheOneRulingR2Chose() {
        assertThat(configuredModel).isEqualTo("claude-sonnet-5");
    }
}
