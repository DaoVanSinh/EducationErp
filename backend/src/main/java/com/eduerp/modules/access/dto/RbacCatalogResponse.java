package com.eduerp.modules.access.dto;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.shared.NamedReference;
import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ dữ liệu tham chiếu của màn hình quản trị RBAC trong một lần gọi. Các danh sách này luôn
 * được dùng cùng nhau (mỗi dropdown một cái), và đều nhỏ, nên bốn request riêng chỉ tạo thêm bốn
 * trạng thái loading rời rạc.
 *
 * <p>{@link RoleOption} và {@link PermissionGroupOption} vẫn có {@code id} + {@code name} như
 * {@link NamedReference}, nên các dropdown cũ dùng tiếp được; phần còn lại là chi tiết cho màn Vai trò:
 * vai trò gồm những nhóm quyền nào, nhóm quyền gồm những quyền nào ở phạm vi nào (spec mục 5).
 */
public record RbacCatalogResponse(
        List<RoleOption> roles,
        List<NamedReference> groups,
        List<PermissionGroupOption> permissionGroups,
        List<NamedReference> branches,
        List<PermissionOption> permissions) {

    public record PermissionOption(UUID id, String resource, String action) {
    }

    /**
     * @param locked vai trò ADMIN - chỉ xem, không sửa (chống tự khoá hệ thống)
     * @param accountCount số tài khoản đang giữ vai trò
     */
    public record RoleOption(UUID id, String name, String code, boolean systemDefault, boolean locked,
            List<UUID> permissionGroupIds, long accountCount) {
    }

    public record PermissionGroupOption(UUID id, String name, String description, boolean locked,
            List<PermissionGroupItemView> items) {
    }

    public record PermissionGroupItemView(UUID permissionId, String resource, String action,
            AccessConstants.PermissionScope scope) {
    }
}
