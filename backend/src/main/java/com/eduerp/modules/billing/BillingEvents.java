package com.eduerp.modules.billing;

import java.util.UUID;

/**
 * {@code actorAccountId} của {@link InvoiceOverdue} là {@code null}: sự kiện do scheduler tự sinh,
 * không có người nào bấm. {@code modules.audit} phải ghi được dòng log với actor rỗng (cột
 * {@code audit_logs.actor_account_id} vốn nullable - xem Task 23).
 */
public final class BillingEvents {

    private BillingEvents() {
    }

    public record InvoiceCreated(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PaymentReceived(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record InvoiceOverdue(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }

    /** Final review Important #9: huỷ hoá đơn là voiding một chứng từ tài chính - phải có dấu vết ai
     * đã làm, không được lẳng lặng (khác InvoiceOverdue, huỷ luôn có người bấm, không null). */
    public record InvoiceCancelled(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }
}
