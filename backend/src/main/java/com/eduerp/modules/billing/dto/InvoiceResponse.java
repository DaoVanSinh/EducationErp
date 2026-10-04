package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 *
 * <p>Từ V22, {@code enrollmentId}/{@code courseId} nullable và {@code comboId} nullable: một hoá
 * đơn thuộc về ĐÚNG MỘT trong hai nhánh (ghi danh hoặc combo). Hai field neo đó đặt cạnh nhau để
 * người đọc thấy ngay chúng loại trừ nhau. {@code installmentNumber} vẫn có nghĩa ở cả hai nhánh -
 * với combo là "đợt N của combo" (spec mục 5).
 */
public record InvoiceResponse(UUID id, UUID enrollmentId, UUID comboId, UUID studentProfileId, UUID courseId,
        UUID branchId, int installmentNumber, BigDecimal amount, BigDecimal amountPaid,
        BillingConstants.InvoiceStatus status, LocalDate dueDate, Instant issuedAt) {
}
