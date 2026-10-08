package com.eduerp.modules.access.internal.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Role của một account — bảng {@code account_roles}, thay cho cột {@code accounts.role_id} trước
 * đây. {@code accountId} là UUID trần, chỉ có FK mức DB tới {@code accounts.id} (V8 migration),
 * KHÔNG có quan hệ JPA sang entity {@code Account} của module identity — access phải tự đủ dữ liệu
 * để tính quyền hiệu lực mà không đụng bảng của module khác (rule #3).
 *
 * <p>Một account chỉ giữ một role, nên khoá chính chính là {@code accountId}.
 */
@Entity
@Table(name = "account_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleAssignment {

    @Id
    private UUID accountId;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    public AccountRoleAssignment(UUID accountId, Role role) {
        this.accountId = accountId;
        this.role = role;
    }

    public void changeRole(Role role) {
        this.role = role;
    }
}
