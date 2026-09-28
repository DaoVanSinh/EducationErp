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

    public DashboardStatsResponse execute() {
        var accountCounts = identity.accountCounts();
        var roleHeadcounts = access.roleHeadcounts().stream()
                .map(row -> new DashboardStatsResponse.RoleHeadcount(row.roleCode(), row.roleName(),
                        row.accountCount()))
                .toList();
        return new DashboardStatsResponse(
                accountCounts.total(), accountCounts.active(), accountCounts.disabled(),
                organization.count(), roleHeadcounts, recentLogins());
    }

    /**
     * Audit log cố tình không có khoá ngoại tới accounts, nên tài khoản đã xoá vẫn còn dòng đăng nhập.
     * Những dòng đó bị bỏ qua ở đây thay vì hiện ra với tên trống.
     */
    private List<RecentLoginResponse> recentLogins() {
        var logins = audit.recentActions(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.LOGIN,
                RECENT_LOGIN_LIMIT);
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
