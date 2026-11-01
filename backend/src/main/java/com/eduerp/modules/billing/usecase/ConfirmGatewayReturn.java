package com.eduerp.modules.billing.usecase;

import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang {@code /payment/return/:gateway} gửi lại đúng bộ tham số mà cổng gắn vào Return URL khi
 * chuyển người trả tiền về. VNPay ký Return URL bằng CÙNG công thức HMAC với IPN, nên sau khi chữ ký
 * hợp lệ thì bộ tham số này có giá trị y như một lần IPN - xử lý qua {@link HandlePaymentCallback}
 * (idempotent: payment không còn PENDING thì bỏ qua, nên IPN và Return về trước/sau đều không cộng
 * tiền hai lần).
 *
 * <p>Vì sao cần: IPN chỉ tới được khi URL IPN đã đăng ký với VNPay và server public. Ở sandbox/demo
 * IPN hay không tới, giao dịch kẹt PENDING dù người dùng đã trả. Return URL luôn tới (trình duyệt
 * của chính người trả mang về), nên đây là đường dự phòng - IPN vẫn là đường chính.
 *
 * <p>Chữ ký sai → {@link InvalidCallbackSignatureException} (400) và KHÔNG tra cứu gì thêm.
 */
@Service
public class ConfirmGatewayReturn {

    private final PaymentGatewayClientResolver gateways;
    private final HandlePaymentCallback handlePaymentCallback;
    private final GetPaymentStatus getPaymentStatus;

    ConfirmGatewayReturn(PaymentGatewayClientResolver gateways, HandlePaymentCallback handlePaymentCallback,
            GetPaymentStatus getPaymentStatus) {
        this.gateways = gateways;
        this.handlePaymentCallback = handlePaymentCallback;
        this.getPaymentStatus = getPaymentStatus;
    }

    @Transactional
    public PaymentResponse execute(PaymentGatewayType gatewayType, Map<String, String> rawParams) {
        String orderId;
        try {
            orderId = gateways.resolve(gatewayType).verifyCallback(rawParams).orderId();
        } catch (PaymentSignatureException badSignature) {
            throw new InvalidCallbackSignatureException(gatewayType);
        }
        handlePaymentCallback.execute(gatewayType, rawParams);
        return getPaymentStatus.execute(orderId);
    }
}
