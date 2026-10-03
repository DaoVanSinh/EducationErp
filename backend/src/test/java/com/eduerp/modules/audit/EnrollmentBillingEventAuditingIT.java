package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.enrollment.EnrollmentEvents;
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
 * {@code PayrollEventAuditingIT}.
 */
@Testcontainers
@SpringBootTest
class EnrollmentBillingEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @TestConfiguration
    static class TransactionalPublisherConfig {
        @Bean
        TransactionalPublisher enrollmentBillingPublisher(ApplicationEventPublisher events) {
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

    private void assertAudited(String entityType, String action, UUID entityId) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(entityType, action, 50).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(entityId.toString());
        });
    }

    @Test
    void auditsEnrollmentCreated() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentCreated(enrollmentId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_CREATE,
                enrollmentId);
    }

    @Test
    void auditsEnrollmentWithdrawn() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentWithdrawn(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_WITHDRAW,
                enrollmentId);
    }

    @Test
    void auditsEnrollmentCompleted() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentCompleted(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_COMPLETE,
                enrollmentId);
    }

    @Test
    void auditsInvoiceCreated() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.InvoiceCreated(invoiceId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.INVOICE_CREATE, invoiceId);
    }

    @Test
    void auditsPaymentReceived() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.PaymentReceived(invoiceId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.PAYMENT_RECEIVED, invoiceId);
    }

    /**
     * {@code InvoiceOverdue} do scheduler tự sinh nên {@code actorAccountId} là {@code null}. Cột
     * {@code audit_logs.actor_account_id} vốn nullable (V7) và {@code AuditLog} không validate gì, nên
     * listener chỉ cần truyền thẳng - test này chốt lại rằng dòng log vẫn được ghi, không bị
     * ném ra lỗi NOT NULL như một số hệ thống khác.
     */
    @Test
    void auditsInvoiceOverdueEvenWithoutAnActor() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.InvoiceOverdue(invoiceId, null, UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.INVOICE_OVERDUE, invoiceId);
    }
}
