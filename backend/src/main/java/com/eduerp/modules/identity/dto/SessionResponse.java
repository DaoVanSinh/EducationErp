package com.eduerp.modules.identity.dto;

import com.eduerp.modules.access.AccessConstants;
import java.util.List;
import java.util.UUID;

/**
 * Tất cả những gì giao diện cần biết về người đang đăng nhập. Gộp hồ sơ và quyền vào một response
 * vì cả hai đều phải có mặt trước khi vẽ được khung màn hình — tách đôi chỉ tạo thêm một trạng thái
 * "đã biết là ai nhưng chưa biết được làm gì".
 */
public record SessionResponse(
        UUID accountId,
        String email,
        String fullName,
        String avatarUrl,
        String roleCode,
        String roleName,
        UUID branchId,
        String branchName,
        List<GrantedPermission> permissions) {

    public record GrantedPermission(String resource, String action, AccessConstants.PermissionScope scope) {
    }
}
