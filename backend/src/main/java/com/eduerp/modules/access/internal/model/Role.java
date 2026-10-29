package com.eduerp.modules.access.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private boolean systemDefault;

    @ManyToMany
    @JoinTable(name = "role_permission_groups",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_group_id"))
    private final Set<PermissionGroup> permissionGroups = new HashSet<>();

    public Role(String code, String name, boolean systemDefault) {
        this.code = code;
        this.name = name;
        this.systemDefault = systemDefault;
    }

    public void addPermissionGroup(PermissionGroup group) {
        permissionGroups.add(group);
    }

    /** Mã vai trò bất biến (đã được tham chiếu ở seed/log); chỉ tên hiển thị đổi được. */
    public void rename(String name) {
        this.name = name;
    }

    public void replacePermissionGroups(java.util.Collection<PermissionGroup> groups) {
        permissionGroups.clear();
        permissionGroups.addAll(groups);
    }

    public Set<PermissionGroup> getPermissionGroups() {
        return Collections.unmodifiableSet(permissionGroups);
    }
}
