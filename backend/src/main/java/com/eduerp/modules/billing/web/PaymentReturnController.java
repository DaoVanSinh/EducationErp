package com.eduerp.modules.billing.web;

import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.usecase.ConfirmGatewayReturn;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trang {@code /payment/return/vnpay} của frontend chuyển nguyên query string VNPay gắn vào Return
 * URL tới đây. PUBLIC (người trả có thể chưa đăng nhập); xác thực bằng chữ ký HMAC trong
 * {@link ConfirmGatewayReturn}. Trả trạng thái thật của giao dịch sau khi xử lý.
 */
@RestController
class PaymentReturnController {

    private final ConfirmGatewayReturn confirmGatewayReturn;

    PaymentReturnController(ConfirmGatewayReturn confirmGatewayReturn) {
        this.confirmGatewayReturn = confirmGatewayReturn;
    }

    @GetMapping(BillingPublicPaths.VNPAY_RETURN)
    PaymentResponse confirmVnpayReturn(@RequestParam Map<String, String> params) {
        return confirmGatewayReturn.execute(PaymentGatewayType.VNPAY, params);
    }
}
