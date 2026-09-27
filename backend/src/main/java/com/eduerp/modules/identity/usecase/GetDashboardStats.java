package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.DashboardStatsResponse;
import com.eduerp.modules.identity.dto.RecentLoginResponse;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.model.AuditLog;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.AuditLogRepository;
import com.eduerp.modules.identity.internal.repository.BranchRepository;
import com.eduerp.modules.identity.internal.repository.RoleHeadcountRow;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDashboardStats {

    /** Dashboard chỉ cần đủ dòng để nhìn thấy ai vừa vào hệ thống; xem đầy đủ là việc của audit log. */
    private static final int RECENT_LOGIN_LIMIT = 10;

    private final AccountRepository accounts;
    private final BranchRepository branches;
    private final AuditLogRepository auditLogs;

    GetDashboardStats(AccountRepository accounts, BranchRepository branches, AuditLogRepository auditLogs) {
        this.accounts = accounts;
        this.branches = branches;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse execute() {
        return new DashboardStatsResponse(
                accounts.count(),
                accounts.countByStatus(IdentityConstants.AccountStatus.ACTIVE),
                accounts.countByStatus(IdentityConstants.AccountStatus.DISABLED),
                branches.count(),
                accounts.countAccountsByRole().stream().map(GetDashboardStats::toHeadcount).toList(),
                recentLogins());
    }

    private static DashboardStatsResponse.RoleHeadcount toHeadcount(RoleHeadcountRow row) {
        return new DashboardStatsResponse.RoleHeadcount(row.getRoleCode(), row.getRoleName(), row.getAccountCount());
    }

    /**
     * Audit log cố tình không có khoá ngoại tới accounts, nên tài khoản đã xoá vẫn còn dòng đăng nhập.
     * Những dòng đó bị bỏ qua ở đây thay vì hiện ra với tên trống.
     */
    private List<RecentLoginResponse> recentLogins() {
        var logins = auditLogs.findByEntityTypeAndActionOrderByOccurredAtDesc(IdentityConstants.Resources.ACCOUNT,
                IdentityConstants.AuditActions.LOGIN, PageRequest.of(0, RECENT_LOGIN_LIMIT));
        Map<UUID, Account> actors = accounts.findAllById(logins.stream()
                        .map(AuditLog::getActorAccountId)
                        .filter(Objects::nonNull)
                        .toList()).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        return logins.stream()
                .filter(login -> actors.containsKey(login.getActorAccountId()))
                .map(login -> {
                    var actor = actors.get(login.getActorAccountId());
                    return new RecentLoginResponse(actor.getId(), actor.getEmail(), actor.getFullName(),
                            login.getOccurredAt());
                })
                .toList();
    }
}
