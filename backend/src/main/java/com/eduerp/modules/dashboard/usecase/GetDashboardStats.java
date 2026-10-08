package com.eduerp.modules.dashboard.usecase;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.dashboard.dto.DashboardStatsResponse;
import com.eduerp.modules.dashboard.dto.RecentLoginResponse;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetDashboardStats {

    /** Dashboard chỉ cần đủ dòng để nhìn thấy ai vừa vào hệ thống; xem đầy đủ là việc của audit log. */
    private static final int RECENT_LOGIN_LIMIT = 10;

    private final IdentityManagement identity;
    private final AccessManagement access;
    private final OrganizationManagement organization;
    private final AuditManagement audit;

    GetDashboardStats(IdentityManagement identity, AccessManagement access, OrganizationManagement organization,
            AuditManagement audit) {
        this.identity = identity;
        this.access = access;
        this.organization = organization;
        this.audit = audit;
    }

    /**
     * {@code branchId == null} là "toàn tổ chức" (hành vi gốc, không lọc gì). Số chi nhánh
     * ({@code organization.count()}) không bao giờ bị lọc theo branchId — đó là một con số toàn cục,
     * không phụ thuộc đang xem chi nhánh nào.
     */
    public DashboardStatsResponse execute(UUID branchId) {
        var accountCounts = branchId == null ? identity.accountCounts() : identity.accountCounts(branchId);
        var roleHeadcountSource = branchId == null
                ? access.roleHeadcounts()
                : access.roleHeadcounts(identity.accountIdsByBranch(branchId));
        var roleHeadcounts = roleHeadcountSource.stream()
                .map(row -> new DashboardStatsResponse.RoleHeadcount(row.roleCode(), row.roleName(),
                        row.accountCount()))
                .toList();
        return new DashboardStatsResponse(
                accountCounts.total(), accountCounts.active(), accountCounts.disabled(),
                organization.count(), roleHeadcounts, recentLogins(branchId));
    }

    /**
     * Audit log cố tình không có khoá ngoại tới accounts, nên tài khoản đã xoá vẫn còn dòng đăng nhập.
     * Những dòng đó bị bỏ qua ở đây thay vì hiện ra với tên trống.
     */
    private List<RecentLoginResponse> recentLogins(UUID branchId) {
        var logins = branchId == null
                ? audit.recentActions(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.LOGIN,
                        RECENT_LOGIN_LIMIT)
                : audit.recentActions(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.LOGIN,
                        RECENT_LOGIN_LIMIT, branchId);
        var actorIds = logins.stream().map(login -> login.actorAccountId()).filter(Objects::nonNull).toList();
        var actors = identity.summariesOf(actorIds);
        return logins.stream()
                .filter(login -> actors.containsKey(login.actorAccountId()))
                .map(login -> {
                    var actor = actors.get(login.actorAccountId());
                    return new RecentLoginResponse(actor.id(), actor.email(), actor.fullName(), login.occurredAt());
                })
                .toList();
    }
}
