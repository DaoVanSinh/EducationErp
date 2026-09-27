package com.eduerp.modules.identity.internal.model;

import com.eduerp.modules.identity.IdentityConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permission_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionGroup {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String description;

    @OneToMany(mappedBy = "permissionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<PermissionGroupItem> items = new ArrayList<>();

    public PermissionGroup(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void addItem(Permission permission, IdentityConstants.PermissionScope scope) {
        items.add(new PermissionGroupItem(this, permission, scope));
    }

    public List<PermissionGroupItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
