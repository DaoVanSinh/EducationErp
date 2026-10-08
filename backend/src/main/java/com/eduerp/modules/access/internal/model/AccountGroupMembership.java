package com.eduerp.modules.access.internal.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một account thuộc một group — bảng {@code account_groups} (có từ V4), đổi chủ sở hữu JPA sang
 * access. {@code accountId} là UUID trần, chỉ FK mức DB tới {@code accounts.id}, không quan hệ JPA
 * sang {@code Account} — cùng lý do với {@link AccountRoleAssignment}.
 */
@Entity
@Table(name = "account_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountGroupMembership {

    @EmbeddedId
    private Key id;

    @ManyToOne
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    public AccountGroupMembership(UUID accountId, Group group) {
        this.id = new Key(accountId, group.getId());
        this.group = group;
    }

    public UUID getAccountId() {
        return id.accountId;
    }

    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        private UUID accountId;
        private UUID groupId;

        Key(UUID accountId, UUID groupId) {
            this.accountId = accountId;
            this.groupId = groupId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return Objects.equals(accountId, other.accountId) && Objects.equals(groupId, other.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, groupId);
        }
    }
}
