package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Không gọi mạng thật (spec mục 11): test chỉ chạm phần thuần - chuỗi ký và HMAC. Giá trị kỳ vọng
 * đến từ hai nguồn độc lập với code production: hex literal tính sẵn ngoài dự án, và
 * {@link #referenceHmacSha256} cài lại HMAC-SHA256 trong chính test.
 */
class MomoPaymentGatewayClientTest {

    private static final String PARTNER_CODE = "PARTNER01";
    private static final String ACCESS_KEY = "ACCESS123";
    private static final String SECRET_KEY = "MOMOSECRET123";
    private static final String ENDPOINT = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String REDIRECT_URL = "https://eduerp.local/payment/return/momo";
    private static final String IPN_URL = "https://eduerp.local/api/billing/payments/callback/momo";

    private final MomoProperties properties =
            new MomoProperties(PARTNER_CODE, ACCESS_KEY, SECRET_KEY, ENDPOINT, REDIRECT_URL, IPN_URL);
    private final MomoPaymentGatewayClient client =
            new MomoPaymentGatewayClient(properties, RestClient.builder());

    private static String referenceHmacSha256(String secret, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
    void typeIsMomo() {
        assertThat(client.type()).isEqualTo(PaymentGatewayType.MOMO);
    }

    /** Thứ tự khoá của chuỗi ký tạo link là CỐ ĐỊNH theo spec mục 2, không phải sắp alphabet ngẫu
     * nhiên - đổi một dấu & là MoMo trả về INVALID_SIGNATURE. */
    @Test
    void createRequestPayloadMatchesTheKeyOrderFromTheSpec() {
        var payload = MomoPaymentGatewayClient.createRequestPayloadToSign(ACCESS_KEY, "50000", "", IPN_URL,
                "order-1", "Hoc phi dot 1", PARTNER_CODE, REDIRECT_URL, "req-1", "captureWallet");

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=50000&extraData="
                + "&ipnUrl=https://eduerp.local/api/billing/payments/callback/momo"
                + "&orderId=order-1&orderInfo=Hoc phi dot 1&partnerCode=PARTNER01"
                + "&redirectUrl=https://eduerp.local/payment/return/momo"
                + "&requestId=req-1&requestType=captureWallet");
    }

    /** Nguồn độc lập #1: hex literal tính sẵn ngoài dự án cho đúng payload ở test trên. */
    @Test
    void createRequestSignatureMatchesThePinnedVector() {
        var payload = MomoPaymentGatewayClient.createRequestPayloadToSign(ACCESS_KEY, "50000", "", IPN_URL,
                "order-1", "Hoc phi dot 1", PARTNER_CODE, REDIRECT_URL, "req-1", "captureWallet");

        assertThat(MomoPaymentGatewayClient.hmacSha256Hex(SECRET_KEY, payload))
                .isEqualTo("cca848a7e943ba2cd3569b8bf2225e3021ccd308ebf88ffe74c0ab339cb441a6");
    }

    /** Nguồn độc lập #2: HMAC tính lại trong test bằng javax.crypto.Mac. */
    @Test
    void hmacImplementationAgreesWithAnIndependentJdkComputation() {
        var data = "accessKey=ACCESS123&amount=1";

        assertThat(MomoPaymentGatewayClient.hmacSha256Hex(SECRET_KEY, data))
                .isEqualTo(referenceHmacSha256(SECRET_KEY, data));
    }

    private Map<String, String> successfulIpnParams() {
        var params = new HashMap<String, String>();
        params.put("partnerCode", PARTNER_CODE);
        params.put("orderId", "order-1");
        params.put("requestId", "req-1");
        params.put("amount", "50000");
        params.put("orderInfo", "Hoc phi dot 1");
        params.put("orderType", "momo_wallet");
        params.put("transId", "2345678901");
        params.put("resultCode", "0");
        params.put("message", "Successful.");
        params.put("payType", "qr");
        params.put("responseTime", "1767222123000");
        params.put("extraData", "");
        params.put("signature", "87d1bf85b084426172542e76dbd6a20e25243046dfcd101d84bf551484bc4d49");
        return params;
    }

    @Test
    void ipnPayloadMatchesTheDocumentedKeyOrder() {
        var payload = MomoPaymentGatewayClient.ipnPayloadToSign(successfulIpnParams(), ACCESS_KEY);

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=50000&extraData=&message=Successful."
                + "&orderId=order-1&orderInfo=Hoc phi dot 1&orderType=momo_wallet&partnerCode=PARTNER01"
                + "&payType=qr&requestId=req-1&responseTime=1767222123000&resultCode=0&transId=2345678901");
    }

    @Test
    void verifyCallbackAcceptsThePinnedIpnSignature() {
        var result = client.verifyCallback(successfulIpnParams());

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.success()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(result.rawMessage()).contains("Successful.");
    }

    @Test
    void verifyCallbackReportsAFailedTransactionWithoutRejectingTheSignature() {
        var params = successfulIpnParams();
        params.put("resultCode", "1006");
        params.put("message", "Transaction denied.");
        params.put("signature",
                referenceHmacSha256(SECRET_KEY, MomoPaymentGatewayClient.ipnPayloadToSign(params, ACCESS_KEY)));

        var result = client.verifyCallback(params);

        assertThat(result.success()).isFalse();
        assertThat(result.orderId()).isEqualTo("order-1");
    }

    @Test
    void verifyCallbackRejectsATamperedAmount() {
        var params = successfulIpnParams();
        params.put("amount", "1");

        assertThatThrownBy(() -> client.verifyCallback(params)).isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void verifyCallbackRejectsAMissingSignature() {
        var params = successfulIpnParams();
        params.remove("signature");

        assertThatThrownBy(() -> client.verifyCallback(params)).isInstanceOf(PaymentSignatureException.class);
    }

    /** Tham số thiếu được coi là chuỗi rỗng trong payload - không được ném NullPointerException, vì
     * đó là đường vào từ Internet, ai cũng POST được JSON thiếu field. */
    @Test
    void ipnPayloadTreatsMissingParametersAsEmptyStrings() {
        var payload = MomoPaymentGatewayClient.ipnPayloadToSign(Map.of("orderId", "order-1"), ACCESS_KEY);

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=&extraData=&message=&orderId=order-1"
                + "&orderInfo=&orderType=&partnerCode=&payType=&requestId=&responseTime=&resultCode="
                + "&transId=");
    }
}
