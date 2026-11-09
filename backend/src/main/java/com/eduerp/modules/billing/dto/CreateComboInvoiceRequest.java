package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Lập lại hoá đơn combo đã huỷ. Số tiền là tổng sau giảm giá, thu đủ một lần. */
public record CreateComboInvoiceRequest(@NotNull UUID comboId) {
}
