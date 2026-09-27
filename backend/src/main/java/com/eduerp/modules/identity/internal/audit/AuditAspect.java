package com.eduerp.modules.identity.internal.audit;

import com.eduerp.modules.identity.AccountPrincipal;
import com.eduerp.modules.identity.internal.model.AuditLog;
import com.eduerp.modules.identity.internal.repository.AuditLogRepository;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
// Đặt thứ tự tường minh để nằm NGOÀI advice của @Transactional (số nhỏ hơn = bọc ngoài). Nếu để
// mặc định thì cả hai cùng LOWEST_PRECEDENCE và thứ tự không xác định — nghĩa là không biết dòng
// audit được ghi trước hay sau khi commit. Ở ngoài là lựa chọn đúng: chỉ hành động đã commit mới
// được ghi, một transaction rollback lúc commit sẽ không để lại audit của việc chưa xảy ra.
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class AuditAspect {

    private final AuditLogRepository auditLogs;
    private final CurrentActor currentActor;

    AuditAspect(AuditLogRepository auditLogs, CurrentActor currentActor) {
        this.auditLogs = auditLogs;
        this.currentActor = currentActor;
    }

    /** Ghi sau khi method trả về: một hành động ném exception là hành động đã không xảy ra. */
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        var actor = currentActor.principal();
        auditLogs.save(new AuditLog(
                actor.map(AccountPrincipal::accountId).orElse(null),
                audited.action(),
                audited.entityType(),
                entityIdOf(result, joinPoint.getArgs()),
                actor.map(AccountPrincipal::homeBranchId).orElse(null)));
        return result;
    }

    /**
     * Chỉ nhận UUID, không bao giờ gọi {@code toString()} lên DTO. Các request DTO của module này
     * mang mật khẩu dạng plaintext ({@code LoginRequest}, {@code ChangePasswordRequest}), nên một
     * aspect "ghi đối số đầu tiên cho tiện" sẽ đổ mật khẩu thẳng vào bảng audit.
     *
     * <p>Ưu tiên giá trị trả về vì với hành động tạo mới thì id của thứ vừa tạo chỉ tồn tại ở đó.
     */
    private static String entityIdOf(Object result, Object[] args) {
        if (result instanceof UUID createdId) {
            return createdId.toString();
        }
        for (Object arg : args) {
            if (arg instanceof UUID targetId) {
                return targetId.toString();
            }
        }
        return null;
    }
}
