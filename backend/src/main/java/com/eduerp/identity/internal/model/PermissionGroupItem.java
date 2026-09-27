package com.eduerp.identity.internal.model;

import com.eduerp.identity.IdentityConstants;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permission_group_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionGroupItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "permission_group_id", nullable = false)
    private PermissionGroup permissionGroup;

    @ManyToOne
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    private IdentityConstants.PermissionScope scope;

    public PermissionGroupItem(PermissionGroup permissionGroup, Permission permission, IdentityConstants.PermissionScope scope) {
        this.permissionGroup = permissionGroup;
        this.permission = permission;
        this.scope = scope;
    }
}
