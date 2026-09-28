package com.eduerp.modules.audit;

import com.eduerp.modules.audit.dto.RecentAuditAction;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Facade của module audit — type DUY NHẤT mà module khác được phép gọi (rule #1). Chỉ đọc. */
@Service
public class AuditManagement {

    private final AuditLogRepository auditLogs;

    AuditManagement(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** N hành động gần nhất của một (entityType, action) — vd dashboard cần "10 lượt đăng nhập gần nhất". */
    @Transactional(readOnly = true)
    public List<RecentAuditAction> recentActions(String entityType, String action, int limit) {
        return auditLogs.findByEntityTypeAndActionOrderByOccurredAtDesc(entityType, action, PageRequest.of(0, limit))
                .stream()
                .map(log -> new RecentAuditAction(log.getActorAccountId(), log.getEntityId(), log.getOccurredAt()))
                .toList();
    }
}
