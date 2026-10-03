package com.eduerp.modules.identity.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.usecase.SeedDefaultAdmin;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DefaultAdminSeederIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    SeedDefaultAdmin seedDefaultAdmin;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    IdentityProperties properties;

    @Test
    void seedsTheDefaultAdminOnStartup() {
        var admin = accounts.findByTypedEmail("admin@eduerp.local");

        assertThat(admin).isPresent();
        assertThat(access.roleOf(admin.orElseThrow().getId()))
                .hasValueSatisfying(role -> assertThat(role.code()).isEqualTo(AccessConstants.RoleCodes.ADMIN));
    }

    /** Chạy lại lần hai không được tạo thêm tài khoản — nếu không, mỗi lần khởi động lại sinh một Admin. */
    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        long before = accounts.count();

        seedDefaultAdmin.execute();

        assertThat(accounts.count()).isEqualTo(before);
    }

    /**
     * Tài khoản Admin mặc định không đi qua luồng mời (mật khẩu đến từ cấu hình, không phải email) -
     * nếu không được đánh dấu "đã kích hoạt" ngay từ lúc seed, lần đăng nhập đầu tiên sẽ bị chặn bởi
     * cổng lastLogin==null + không có invite token trong Redis, tức không ai vào được hệ thống lần nào.
     */
    @Test
    void seededAdminCanLogInImmediatelyWithoutAnInvite() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(properties.defaultAdminEmail(), properties.defaultAdminPassword())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"requiresPasswordChange\":false");
    }
}
