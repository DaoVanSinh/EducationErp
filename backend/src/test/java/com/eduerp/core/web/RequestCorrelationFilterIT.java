package com.eduerp.core.web;

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
class RequestCorrelationFilterIT {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    /** Kể cả request bị chặn (401) cũng phải có request-id — log lỗi xác thực mới nối được với client. */
    @Test
    void everyResponseCarriesARequestId() throws Exception {
        var result = mockMvc.perform(get("/api/dashboard/stats")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void echoesBackARequestIdTheCallerAlreadySent() throws Exception {
        var result = mockMvc.perform(get("/api/dashboard/stats").header(REQUEST_ID_HEADER, "qa-fixed-id"))
                .andReturn();

        assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER)).isEqualTo("qa-fixed-id");
    }
}
