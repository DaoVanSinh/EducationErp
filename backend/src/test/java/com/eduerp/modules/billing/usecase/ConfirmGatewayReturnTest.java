package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmGatewayReturnTest {

    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final HandlePaymentCallback handlePaymentCallback = mock(HandlePaymentCallback.class);
    private final GetPaymentStatus getPaymentStatus = mock(GetPaymentStatus.class);
    private final ConfirmGatewayReturn useCase =
            new ConfirmGatewayReturn(resolver, handlePaymentCallback, getPaymentStatus);

    private final Map<String, String> params = Map.of("vnp_TxnRef", "order-1", "vnp_SecureHash", "abc");

    @Test
    void appliesTheSignedReturnLikeAnIpnThenReportsTheRealStatus() {
        when(resolver.resolve(PaymentGatewayType.VNPAY)).thenReturn(client);
        when(client.verifyCallback(params))
                .thenReturn(new PaymentCallbackResult("order-1", true, new BigDecimal("2000000"), "vnp_ResponseCode=00"));
        var status = new PaymentResponse(UUID.randomUUID(), new BigDecimal("2000000"),
                BillingConstants.PaymentMethod.VNPAY, BillingConstants.PaymentStatus.SUCCESS, Instant.now(),
                Instant.now(), null);
        when(getPaymentStatus.execute("order-1")).thenReturn(status);

        var response = useCase.execute(PaymentGatewayType.VNPAY, params);

        verify(handlePaymentCallback).execute(PaymentGatewayType.VNPAY, params);
        assertThat(response).isEqualTo(status);
    }

    /** Chữ ký sai: từ chối ngay, không đụng tới sổ thanh toán và không tra trạng thái. */
    @Test
    void rejectsATamperedReturnWithoutTouchingAnything() {
        when(resolver.resolve(PaymentGatewayType.VNPAY)).thenReturn(client);
        when(client.verifyCallback(params)).thenThrow(new PaymentSignatureException(PaymentGatewayType.VNPAY));

        assertThatThrownBy(() -> useCase.execute(PaymentGatewayType.VNPAY, params))
                .isInstanceOf(InvalidCallbackSignatureException.class);
        verify(handlePaymentCallback, never()).execute(any(), any());
        verify(getPaymentStatus, never()).execute(any());
    }
}
