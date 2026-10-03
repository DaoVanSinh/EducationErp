package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Hợp đồng của integrations.payment: chỉ 2 cổng, và PaymentRequest để trống URL nghĩa là "dùng cấu
 * hình của chính client" (modules.billing không đọc được MomoProperties/VnPayProperties). */
class PaymentContractTest {

    @Test
    void supportsExactlyTwoGateways() {
        assertThat(PaymentGatewayType.values())
                .containsExactly(PaymentGatewayType.MOMO, PaymentGatewayType.VNPAY);
    }

    @Test
    void withGatewayDefaultsLeavesBothUrlsEmptySoTheClientUsesItsOwnConfiguration() {
        var request = PaymentRequest.withGatewayDefaults("order-1", new BigDecimal("500000"), "Hoc phi dot 1");

        assertThat(request.orderId()).isEqualTo("order-1");
        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(request.orderInfo()).isEqualTo("Hoc phi dot 1");
        assertThat(request.returnUrl()).isNull();
        assertThat(request.ipnUrl()).isNull();
    }

    @Test
    void signatureExceptionNamesTheGatewayWithoutLeakingAnyOrderId() {
        var ex = new PaymentSignatureException(PaymentGatewayType.VNPAY);

        assertThat(ex.gateway()).isEqualTo(PaymentGatewayType.VNPAY);
        assertThat(ex.getMessage()).contains("VNPAY").doesNotContain("order");
    }
}
