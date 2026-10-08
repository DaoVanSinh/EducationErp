package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollEvents;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code @ApplicationModuleListener} = {@code @TransactionalEventListener} với
 * {@code fallbackExecution=false} mặc định - publish trực tiếp từ test (không có transaction thật)
 * sẽ KHÔNG BAO GIỜ gọi listener, không phải là chạy chậm. Dùng một bean {@code @Transactional} thật
 * (commit thật, không phải transaction test tự rollback) để publish, mirror cách mọi IT khác trong
 * dự án này trigger event qua một lệnh gọi HTTP thật (tự commit ở cuối usecase).
 */
@Testcontainers
@SpringBootTest
class PayrollEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @TestConfiguration
    static class TransactionalPublisherConfig {
        @Bean
        TransactionalPublisher transactionalPublisher(ApplicationEventPublisher events) {
            return new TransactionalPublisher(events);
        }
    }

    @Component
    static class TransactionalPublisher {
        private final ApplicationEventPublisher events;

        TransactionalPublisher(ApplicationEventPublisher events) {
            this.events = events;
        }

        @Transactional
        void publish(Object event) {
            events.publishEvent(event);
        }
    }

    @Autowired
    TransactionalPublisher events;

    @Autowired
    AuditManagement audit;

    @Test
    void auditsContractCreated() {
        var contractId = UUID.randomUUID();
        events.publish(new PayrollEvents.ContractCreated(contractId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_CREATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId.toString());
        });
    }

    @Test
    void auditsContractTerminated() {
        var contractId = UUID.randomUUID();
        events.publish(new PayrollEvents.ContractTerminated(contractId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_TERMINATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId.toString());
        });
    }

    @Test
    void auditsPayrollRunApproved() {
        var runId = UUID.randomUUID();
        events.publish(new PayrollEvents.PayrollRunApproved(runId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.PAYROLL_RUN,
                    AuditConstants.Actions.PAYROLL_RUN_APPROVE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(runId.toString());
        });
    }
}
