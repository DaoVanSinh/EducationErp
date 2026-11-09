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

/** Một lần chuyển chi nhánh chủ quản. Dữ liệu cũ vẫn gắn chi nhánh cũ, bảng này cho biết người đó từng ở đâu. */
@Entity
@Table(name = "account_branch_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBranchHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "from_branch_id", updatable = false)
    private UUID fromBranchId;

    @Column(name = "to_branch_id", nullable = false, updatable = false)
    private UUID toBranchId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by_account_id", nullable = false, updatable = false)
    private UUID changedByAccountId;

    public AccountBranchHistory(UUID accountId, UUID fromBranchId, UUID toBranchId, UUID changedByAccountId) {
        this.accountId = accountId;
        this.fromBranchId = fromBranchId;
        this.toBranchId = toBranchId;
        this.changedAt = Instant.now();
        this.changedByAccountId = changedByAccountId;
    }
}
