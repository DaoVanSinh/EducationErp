package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 *
 * <p>Từ V22, {@code enrollmentId}/{@code courseId} nullable và {@code comboId} nullable: một hoá
 * đơn thuộc về ĐÚNG MỘT trong hai nhánh (ghi danh hoặc combo). Hai field neo đó đặt cạnh nhau để
 * người đọc thấy ngay chúng loại trừ nhau. {@code installmentNumber} vẫn có nghĩa ở cả hai nhánh -
 * với combo là "đợt N của combo" (spec mục 5).
 *
 * <p>{@code studentName} và {@code courseNames} là dữ liệu hiển thị tra lúc đọc. {@code courseNames} có
 * một tên với hoá đơn ghi danh, nhiều tên với hoá đơn combo, và rỗng khi không tra được.
 *
 * <p>{@code studentAccountId} để danh sách quá hạn thao tác được trên tài khoản học viên.
 */
public record InvoiceResponse(UUID id, UUID enrollmentId, UUID comboId, UUID studentProfileId, String studentName,
        UUID courseId, List<String> courseNames, UUID branchId, int installmentNumber, BigDecimal amount,
        BigDecimal amountPaid, BillingConstants.InvoiceStatus status, LocalDate dueDate, Instant issuedAt,
        boolean dueExtended, String extensionReason, UUID studentAccountId) {
}
