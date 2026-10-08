package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Pin thuật toán, không chỉ test round-trip sign→verify (spec mục 11): nếu ký và verify cùng sai một
 * kiểu thì round-trip vẫn xanh. Mỗi assert dưới đây so với MỘT TRONG HAI nguồn độc lập:
 * <ol>
 *   <li>hex literal cố định, tính sẵn bằng công cụ ngoài dự án (Python {@code hmac} + {@code hashlib});</li>
 *   <li>{@link #referenceHmacSha512} - cài lại HMAC-SHA512 + hex ngay trong test bằng
 *       {@code javax.crypto.Mac}, không gọi code production.</li>
 * </ol>
 */
class VnPayPaymentGatewayClientTest {

    private static final String HASH_SECRET = "VNPAYSECRET123";
    private static final String TMN_CODE = "TMN001";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL = "https://eduerp.local/payment/return/vnpay";
    private static final String IPN_URL = "https://eduerp.local/api/billing/payments/callback/vnpay";

    /** 2026-10-03T01:02:03 giờ hệ thống của test - khớp vnp_CreateDate 20261003010203 dưới đây. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T18:02:03Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnPayProperties properties =
            new VnPayProperties(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, IPN_URL);
    private final VnPayPaymentGatewayClient client = new VnPayPaymentGatewayClient(properties, FIXED_CLOCK);

    /** Cài lại HMAC-SHA512 + hex từ đầu, không dùng một dòng code production nào. */
    private static String referenceHmacSha512(String secret, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            var bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void typeIsVnpay() {
        assertThat(client.type()).isEqualTo(PaymentGatewayType.VNPAY);
    }

    /** Nguồn độc lập #1: hex literal tính sẵn ngoài dự án cho đúng chuỗi hashData ở assert đầu. */
    @Test
    void hashDataAndSignatureMatchThePinnedVector() {
        var params = new TreeMap<String, String>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_Amount", "50000000");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "order-1");
        params.put("vnp_OrderInfo", "Hoc phi dot 1");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", RETURN_URL);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", "20261003010203");

        var hashData = VnPayPaymentGatewayClient.hashData(params);

        assertThat(hashData).isEqualTo("vnp_Amount=50000000&vnp_Command=pay&vnp_CreateDate=20261003010203"
                + "&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Hoc+phi+dot+1"
                + "&vnp_OrderType=other"
                + "&vnp_ReturnUrl=https%3A%2F%2Feduerp.local%2Fpayment%2Freturn%2Fvnpay"
                + "&vnp_TmnCode=TMN001&vnp_TxnRef=order-1&vnp_Version=2.1.0");
        assertThat(VnPayPaymentGatewayClient.hmacSha512Hex(HASH_SECRET, hashData)).isEqualTo(
                "562c7e85d55fd0405e85fa3428da53a04a1a84cf3b76048d11354728f2ca8fb4"
                        + "6f4516888217181583e7b1170c4d53305efccd69214b7732d6fb8a4e12fe3f2a");
    }

    /** Nguồn độc lập #2: HMAC tính lại trong test bằng javax.crypto.Mac. */
    @Test
    void hmacImplementationAgreesWithAnIndependentJdkComputation() {
        var data = "vnp_Amount=1&vnp_TxnRef=abc";

        assertThat(VnPayPaymentGatewayClient.hmacSha512Hex(HASH_SECRET, data))
                .isEqualTo(referenceHmacSha512(HASH_SECRET, data));
    }

    @Test
    void createPaymentUrlMultipliesAmountByOneHundredAndAppendsTheSecureHash() {
        var result = client.createPaymentUrl(
                PaymentRequest.withGatewayDefaults("order-1", new BigDecimal("500000"), "Hoc phi dot 1"));

        assertThat(result.gatewayOrderId()).isEqualTo("order-1");
        assertThat(result.payUrl()).startsWith(PAY_URL + "?");
        // Đơn vị của VNPay là xu: 500000 VND -> 50000000.
        assertThat(result.payUrl()).contains("vnp_Amount=50000000");
        assertThat(result.payUrl()).contains("vnp_TxnRef=order-1");
        assertThat(result.payUrl()).contains("vnp_CreateDate=20261003010203");
        assertThat(result.payUrl()).contains("vnp_SecureHash="
                + "562c7e85d55fd0405e85fa3428da53a04a1a84cf3b76048d11354728f2ca8fb4"
                + "6f4516888217181583e7b1170c4d53305efccd69214b7732d6fb8a4e12fe3f2a");
    }

    private Map<String, String> validCallbackParams() {
        var params = new HashMap<String, String>();
        params.put("vnp_Amount", "50000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Hoc phi dot 1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "14012345");
        params.put("vnp_TxnRef", "order-1");
        params.put("vnp_SecureHash",
                "a55d8e2ff60d85c8e41391d39c208b23becd89e33847fee2a6c0eff17aa132a7"
                        + "d45215e64662fb81ded05fd5cd7699d1ce15674e46bbb0153c7d63578be97a9e");
        return params;
    }

    @Test
    void verifyCallbackAcceptsThePinnedSignatureAndConvertsAmountBackToVnd() {
        var result = client.verifyCallback(validCallbackParams());

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.success()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(result.rawMessage()).contains("00");
    }

    /** vnp_SecureHashType cũng phải bị loại khỏi chuỗi hash, nếu không chữ ký thật sẽ bị từ chối. */
    @Test
    void verifyCallbackIgnoresTheSecureHashTypeParameter() {
        var params = validCallbackParams();
        params.put("vnp_SecureHashType", "SHA512");

        assertThat(client.verifyCallback(params).success()).isTrue();
    }

    @Test
    void verifyCallbackAcceptsAnUppercaseSignature() {
        var params = validCallbackParams();
        params.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase());

        assertThat(client.verifyCallback(params).success()).isTrue();
    }

    @Test
    void verifyCallbackReportsAFailedTransactionWithoutRejectingTheSignature() {
        var params = new HashMap<String, String>();
        params.put("vnp_Amount", "50000000");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TxnRef", "order-1");
        var hashData = VnPayPaymentGatewayClient.hashData(new TreeMap<>(params));
        params.put("vnp_SecureHash", referenceHmacSha512(HASH_SECRET, hashData));

        var result = client.verifyCallback(params);

        assertThat(result.success()).isFalse();
        assertThat(result.orderId()).isEqualTo("order-1");
    }

    @Test
    void verifyCallbackRejectsATamperedAmount() {
        var params = validCallbackParams();
        params.put("vnp_Amount", "1");

        assertThatThrownBy(() -> client.verifyCallback(params))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void verifyCallbackRejectsAMissingSignature() {
        var params = validCallbackParams();
        params.remove("vnp_SecureHash");

        assertThatThrownBy(() -> client.verifyCallback(params))
                .isInstanceOf(PaymentSignatureException.class);
    }
}
