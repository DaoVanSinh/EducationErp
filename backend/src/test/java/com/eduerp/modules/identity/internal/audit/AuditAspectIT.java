package com.eduerp.modules.identity.internal.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.internal.model.AuditLog;
import com.eduerp.modules.identity.internal.repository.AuditLogRepository;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Import(AuditAspectIT.SampleAuditedService.class)
class AuditAspectIT {

    private static final String TEST_ACTION = "TEST_ACTION";
    private static final String TEST_ENTITY = "TEST_ENTITY";
    private static final String SECRET_ENTITY = "SECRET_ENTITY";
    private static final String PLAINTEXT_PASSWORD = "Sup3rSecret!";

    /**
     * Bean mẫu chỉ tồn tại trong test này. Khai báo qua {@code @Import} chứ không gắn
     * {@code @Component}, vì một {@code @Component} nằm trong test source sẽ bị component scan nhặt
     * vào mọi context {@code @SpringBootTest} khác của dự án.
     */
    static class SampleAuditedService {

        @Audited(action = TEST_ACTION, entityType = TEST_ENTITY)
        public UUID actOn(UUID entityId) {
            return entityId;
        }

        @Audited(action = TEST_ACTION, entityType = SECRET_ENTITY)
        public void actOn(RequestCarryingASecret request) {
            // Không trả về gì, và đối số duy nhất là DTO — aspect không được lấy entityId từ đây.
        }

        @Audited(action = TEST_ACTION, entityType = TEST_ENTITY)
        public void fail() {
            throw new IllegalStateException("hành động thất bại");
        }
    }

    record RequestCarryingASecret(String email, String password) {
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    SampleAuditedService sampleService;

    @Autowired
    AuditLogRepository auditLogs;

    @Test
    void writesOneRowAfterTheMethodSucceeds() {
        var entityId = UUID.randomUUID();

        sampleService.actOn(entityId);

        var logged = auditLogs.findByEntityType(TEST_ENTITY, Pageable.unpaged()).stream()
                .filter(log -> entityId.toString().equals(log.getEntityId()))
                .toList();
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getAction()).isEqualTo(TEST_ACTION);
        // Không có request đang chạy nên không có actor — cột phải rỗng chứ không phải một id bịa ra.
        assertThat(logged.get(0).getActorAccountId()).isNull();
    }

    /** Bảo vệ tính chất bảo mật: mật khẩu trong DTO không được rơi vào cột entityId. */
    @Test
    void neverSerializesADtoArgumentIntoTheLog() {
        sampleService.actOn(new RequestCarryingASecret("someone@eduerp.local", PLAINTEXT_PASSWORD));

        var logged = auditLogs.findByEntityType(SECRET_ENTITY, Pageable.unpaged());
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getEntityId()).isNull();
        assertThat(auditLogs.findAll()).extracting(AuditLog::getEntityId)
                .doesNotContain(PLAINTEXT_PASSWORD);
    }

    @Test
    void writesNothingWhenTheMethodThrows() {
        long before = auditLogs.count();

        try {
            sampleService.fail();
        } catch (IllegalStateException expected) {
            // Hành động ném exception là hành động đã không xảy ra, không được ghi vào audit.
        }

        assertThat(auditLogs.count()).isEqualTo(before);
    }
}
