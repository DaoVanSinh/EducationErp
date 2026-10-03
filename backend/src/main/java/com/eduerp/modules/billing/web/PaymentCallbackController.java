package com.eduerp.modules.billing.web;

import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.modules.billing.BillingException;
import com.eduerp.modules.billing.usecase.HandlePaymentCallback;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đường vào của cổng thanh toán. PUBLIC, không {@code @PreAuthorize}: MoMo/VNPay gọi
 * server-to-server, không mang session/cookie của ứng dụng (spec mục 5). Xác thực là chữ ký HMAC,
 * kiểm trong usecase.
 *
 * <p><strong>Review Focus #5:</strong> mỗi endpoint trả về MỘT response cố định cho MỌI kết quả -
 * chữ ký sai, {@code orderId} lạ, giao dịch thất bại hay thành công đều giống nhau từng byte. Đây là
 * điểm plan cố ý khác spec mục 5 bước 1 (ở đó viết "trả 400"): một mã trạng thái riêng cho chữ ký
 * sai chính là oracle mà Review Focus #5 cấm. Chẩn đoán vẫn nằm đủ trong log của
 * {@link HandlePaymentCallback}.
 */
@RestController
@RequestMapping("/api/billing/payments/callback")
class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    /** Hợp đồng IPN của VNPay: trả khác chuỗi này là VNPay coi như mình chưa nhận và sẽ gọi lại. */
    private static final String VNPAY_ACK_BODY = "{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}";

    private final HandlePaymentCallback handlePaymentCallback;

    PaymentCallbackController(HandlePaymentCallback handlePaymentCallback) {
        this.handlePaymentCallback = handlePaymentCallback;
    }

    @GetMapping("/vnpay")
    ResponseEntity<String> vnpay(@RequestParam Map<String, String> params) {
        handleQuietly(PaymentGatewayType.VNPAY, params);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(VNPAY_ACK_BODY);
    }

    @PostMapping("/momo")
    ResponseEntity<Void> momo(@RequestBody Map<String, Object> body) {
        handleQuietly(PaymentGatewayType.MOMO, toStringMap(body));
        return ResponseEntity.noContent().build();
    }

    /**
     * Bắt MỌI {@code BillingException} (gồm {@code InvalidCallbackSignatureException}) và trả về
     * đúng response cố định như đường thành công - không để bất kỳ khác biệt nào ra ngoài.
     */
    private void handleQuietly(PaymentGatewayType gatewayType, Map<String, String> params) {
        try {
            handlePaymentCallback.execute(gatewayType, params);
        } catch (BillingException rejected) {
            log.warn("Từ chối callback của cổng {}: {}", gatewayType, rejected.getErrorCode());
        }
    }

    /** MoMo gửi JSON có cả số và chuỗi; chữ ký được tính trên dạng chuỗi của từng giá trị. */
    private static Map<String, String> toStringMap(Map<String, Object> body) {
        var params = new HashMap<String, String>();
        body.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        return params;
    }
}
