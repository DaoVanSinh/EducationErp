package com.eduerp.modules.billing;

import com.eduerp.integrations.payment.PaymentGatewayType;
import org.springframework.http.HttpStatus;

/**
 * Chữ ký callback không khớp. Message cố tình chỉ nêu tên cổng, KHÔNG nêu orderId - Review Focus #5:
 * chuỗi này không được trở thành oracle nếu lọt vào log hay response.
 * {@code PaymentCallbackController} (Task 22) bắt lỗi này và vẫn trả đúng response cố định như mọi
 * trường hợp khác, nên HttpStatus ở đây chỉ dùng cho lời gọi nội bộ/test.
 */
public final class InvalidCallbackSignatureException extends BillingException {
    public InvalidCallbackSignatureException(PaymentGatewayType gateway) {
        super("BILLING_INVALID_CALLBACK_SIGNATURE", HttpStatus.BAD_REQUEST,
                "Chữ ký callback của cổng " + gateway + " không hợp lệ");
    }
}
