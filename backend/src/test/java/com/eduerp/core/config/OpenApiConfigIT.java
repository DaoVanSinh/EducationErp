package com.eduerp.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    /** Tài liệu API phải xem được mà không cần đăng nhập — FE/QA tra cứu contract trước khi có phiên. */
    @Test
    void apiDocsAreReachableWithoutAuthentication() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"openapi\"", "EduERP API");
    }

    @Test
    void swaggerUiIsReachableWithoutAuthentication() throws Exception {
        var result = mockMvc.perform(get("/swagger-ui/index.html")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
