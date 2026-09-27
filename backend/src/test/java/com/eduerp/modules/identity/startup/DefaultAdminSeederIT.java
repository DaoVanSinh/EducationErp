package com.eduerp.modules.identity.startup;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.usecase.SeedDefaultAdmin;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
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
    SeedDefaultAdmin seedDefaultAdmin;

    @Test
    void seedsTheDefaultAdminOnStartup() {
        var admin = accounts.findByTypedEmail("admin@eduerp.local");

        assertThat(admin).isPresent();
        assertThat(admin.orElseThrow().getRole().getCode()).isEqualTo(IdentityConstants.RoleCodes.ADMIN);
    }

    /** Chạy lại lần hai không được tạo thêm tài khoản — nếu không, mỗi lần khởi động lại sinh một Admin. */
    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        long before = accounts.countByRole_Code(IdentityConstants.RoleCodes.ADMIN);

        seedDefaultAdmin.execute();

        assertThat(accounts.countByRole_Code(IdentityConstants.RoleCodes.ADMIN)).isEqualTo(before);
    }
}
