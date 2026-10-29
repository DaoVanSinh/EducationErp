package com.eduerp.modules.access.internal.model;

import com.eduerp.modules.access.AccessConstants;
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

    public void addItem(Permission permission, AccessConstants.PermissionScope scope) {
        items.add(new PermissionGroupItem(this, permission, scope));
    }

    public List<PermissionGroupItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Đồng bộ danh sách quyền về đúng {@code desired} (permissionId → scope) bằng cách SỬA TẠI CHỖ dòng
     * đang có, chỉ xoá dòng thừa và thêm dòng thiếu. Không "xoá hết rồi thêm lại": Hibernate flush lệnh
     * INSERT trước DELETE, nên thêm lại cùng permission trong một lần flush sẽ vi phạm ràng buộc UNIQUE
     * {@code (permission_group_id, permission_id)} của V2.
     */
    public void syncItems(java.util.Map<UUID, Permission> permissionsById,
            java.util.Map<UUID, AccessConstants.PermissionScope> desired) {
        items.removeIf(item -> !desired.containsKey(item.getPermission().getId()));
        var existing = new java.util.HashMap<UUID, PermissionGroupItem>();
        items.forEach(item -> existing.put(item.getPermission().getId(), item));
        desired.forEach((permissionId, scope) -> {
            var current = existing.get(permissionId);
            if (current == null) {
                items.add(new PermissionGroupItem(this, permissionsById.get(permissionId), scope));
            } else {
                current.changeScope(scope);
            }
        });
    }
}
