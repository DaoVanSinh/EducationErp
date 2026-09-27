package com.eduerp.modules.identity.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một dòng lịch sử đã xảy ra: không có setter và không có method sửa, vì bản chất của audit là
 * không được phép chỉnh lại sau khi ghi.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID actorAccountId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    private String entityId;

    private UUID branchId;

    @Column(nullable = false)
    private Instant occurredAt;

    public AuditLog(UUID actorAccountId, String action, String entityType, String entityId, UUID branchId) {
        this.actorAccountId = actorAccountId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.branchId = branchId;
        this.occurredAt = Instant.now();
    }
}
