package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.academic.AcademicEvents;
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
 * sẽ KHÔNG BAO GIỜ gọi listener. Dùng một bean {@code @Transactional} thật để publish, mirror
 * {@code EnrollmentBillingEventAuditingIT}.
 */
@Testcontainers
@SpringBootTest
class AcademicEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @TestConfiguration
    static class TransactionalPublisherConfig {
        @Bean
        TransactionalPublisher academicPublisher(ApplicationEventPublisher events) {
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
    void auditsGradeRecorded() {
        var enrollmentId = UUID.randomUUID();

        events.publish(new AcademicEvents.GradeRecorded(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.GRADE_ENTRY,
                            AuditConstants.Actions.GRADE_RECORD, 50).stream()
                    .map(action -> action.entityId()).toList();
            assertThat(entityIds).contains(enrollmentId.toString());
        });
    }
}
