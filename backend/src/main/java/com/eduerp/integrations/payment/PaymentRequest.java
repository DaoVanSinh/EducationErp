package com.eduerp.integrations.payment;

import java.math.BigDecimal;

/**
 * Yêu cầu tạo link thanh toán. {@code amount} là VND nguyên (scale 0) - VNPay tự nhân 100 bên trong
 * client vì đơn vị của nó là xu.
 *
 * <p>{@code returnUrl}/{@code ipnUrl} để {@code null}/trống nghĩa là "dùng cấu hình của chính
 * client" ({@code MomoProperties}/{@code VnPayProperties}): {@code modules.billing} không đọc được
 * hai property đó (chúng là nội bộ của integration), nên {@link #withGatewayDefaults} là cách gọi
 * bình thường. Hai field vẫn tồn tại để một lời gọi đặc biệt (sandbox, test thủ công) ghi đè được.
 */
public record PaymentRequest(String orderId, BigDecimal amount, String orderInfo, String returnUrl, String ipnUrl) {

    public static PaymentRequest withGatewayDefaults(String orderId, BigDecimal amount, String orderInfo) {
        return new PaymentRequest(orderId, amount, orderInfo, null, null);
    }
}
