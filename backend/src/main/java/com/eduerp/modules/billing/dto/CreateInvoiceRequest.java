package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Lập lại hoá đơn đã huỷ. Số tiền là đủ học phí và hạn là ngày thứ 7 từ khai giảng, hệ thống tự tính. */
public record CreateInvoiceRequest(@NotNull UUID enrollmentId) {
}
