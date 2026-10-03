package com.eduerp.modules.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.UnknownPaymentGatewayException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentGatewayClientResolverTest {

    /** Client giả, không gọi mạng - chỉ cần type() đúng để kiểm tra bảng map. */
    private record StubClient(PaymentGatewayType type) implements PaymentGatewayClient {
        @Override
        public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
            return new PaymentUrlResult("https://stub/" + type, request.orderId());
        }

        @Override
        public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
            throw new UnsupportedOperationException();
        }
    }

    private final PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(
            List.of(new StubClient(PaymentGatewayType.MOMO), new StubClient(PaymentGatewayType.VNPAY)));

    @Test
    void resolvesEachOnlineMethodToTheMatchingClient() {
        assertThat(resolver.resolve(BillingConstants.PaymentMethod.MOMO).type())
                .isEqualTo(PaymentGatewayType.MOMO);
        assertThat(resolver.resolve(BillingConstants.PaymentMethod.VNPAY).type())
                .isEqualTo(PaymentGatewayType.VNPAY);
    }

    @Test
    void refusesManualBecauseItIsNotAGateway() {
        assertThatThrownBy(() -> resolver.resolve(BillingConstants.PaymentMethod.MANUAL))
                .isInstanceOf(UnknownPaymentGatewayException.class)
                .hasMessageContaining("MANUAL");
    }

    /** Nếu một client bị thiếu bean (cấu hình sai, profile tắt), lỗi phải là lỗi nghiệp vụ rõ ràng,
     * không phải NullPointerException ở giữa luồng thanh toán. */
    @Test
    void refusesAMethodWhoseClientBeanIsMissing() {
        var partial = new PaymentGatewayClientResolver(List.of(new StubClient(PaymentGatewayType.MOMO)));

        assertThatThrownBy(() -> partial.resolve(BillingConstants.PaymentMethod.VNPAY))
                .isInstanceOf(UnknownPaymentGatewayException.class);
    }
}
