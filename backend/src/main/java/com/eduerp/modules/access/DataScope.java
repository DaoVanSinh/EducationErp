package com.eduerp.modules.access;

import java.util.UUID;

/**
 * Lớp 2 của RBAC (spec mục 6.4): sau khi {@code @PreAuthorize} đã cho qua, use case hỏi lại "được
 * thấy/sửa dữ liệu của chi nhánh nào". ORGANIZATION không lọc; BRANCH chỉ chi nhánh chủ quản của người
 * gọi; không có quyền (hoặc chỉ PERSONAL) thì không thấy dữ liệu quản trị nào.
 *
 * <p>Tạo qua {@link AccessManagement#dataScopeOf(UUID, String, String)} - module khác không tự dựng.
 *
 * @param scope scope rộng nhất người gọi có cho resource+action, {@code null} nếu không có quyền
 * @param homeBranchId chi nhánh chủ quản, chỉ có nghĩa khi scope là BRANCH
 */
public record DataScope(String resource, String action, AccessConstants.PermissionScope scope, UUID homeBranchId) {

    public boolean isOrganizationWide() {
        return scope == AccessConstants.PermissionScope.ORGANIZATION;
    }

    /** Bản ghi thuộc {@code branchId} có nằm trong phạm vi không. */
    public boolean covers(UUID branchId) {
        if (isOrganizationWide()) {
            return true;
        }
        return scope == AccessConstants.PermissionScope.BRANCH && homeBranchId != null
                && homeBranchId.equals(branchId);
    }

    /** Ném 403 nếu bản ghi thuộc chi nhánh ngoài phạm vi. */
    public void requireCovers(UUID branchId) {
        requireBranchLevel();
        if (!covers(branchId)) {
            throw new AccessException.OutOfBranchScope(branchId);
        }
    }

    /**
     * Bộ lọc chi nhánh cho một truy vấn danh sách. ORGANIZATION giữ nguyên lựa chọn của người gọi
     * ({@code null} = toàn tổ chức); BRANCH luôn bị ép về chi nhánh chủ quản, bất kể client gửi gì.
     */
    public UUID branchFilter(UUID requestedBranchId) {
        requireBranchLevel();
        return isOrganizationWide() ? requestedBranchId : homeBranchId;
    }

    private void requireBranchLevel() {
        if (scope == null || scope == AccessConstants.PermissionScope.PERSONAL) {
            throw new AccessException.ScopeDenied(resource, action);
        }
        if (scope == AccessConstants.PermissionScope.BRANCH && homeBranchId == null) {
            throw new AccessException.NoHomeBranch();
        }
    }
}
