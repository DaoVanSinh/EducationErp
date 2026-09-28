package com.eduerp.modules.identity.dto;

import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ dữ liệu tham chiếu của màn hình quản trị RBAC trong một lần gọi. Các danh sách này luôn
 * được dùng cùng nhau (mỗi dropdown một cái), và đều nhỏ, nên bốn request riêng chỉ tạo thêm bốn
 * trạng thái loading rời rạc.
 */
public record RbacCatalogResponse(
        List<NamedReference> roles,
        List<NamedReference> groups,
        List<NamedReference> permissionGroups,
        List<NamedReference> branches,
        List<PermissionOption> permissions) {

    public record PermissionOption(UUID id, String resource, String action) {
    }
}
