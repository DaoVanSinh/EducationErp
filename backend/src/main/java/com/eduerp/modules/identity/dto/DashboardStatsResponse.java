package com.eduerp.modules.identity.dto;

import java.util.List;

/** Số liệu tổng quan của phân hệ quản trị người dùng. */
public record DashboardStatsResponse(
        long totalAccounts,
        long activeAccounts,
        long disabledAccounts,
        long branchCount,
        List<RoleHeadcount> accountsByRole,
        List<RecentLoginResponse> recentLogins) {

    /**
     * Danh sách chứ không phải Map: thứ tự hiển thị là một quyết định của server, và JSON dạng mảng
     * giữ nguyên thứ tự đó, còn object thì không hứa gì.
     */
    public record RoleHeadcount(String roleCode, String roleName, long accountCount) {
    }
}
