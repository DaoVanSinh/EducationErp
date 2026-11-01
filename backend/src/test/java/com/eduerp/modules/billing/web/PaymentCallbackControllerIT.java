package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.redis.testcontainers.RedisContainer;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Review Focus #5: endpoint IPN của VNPay không được tạo oracle. Test chốt secret qua property để tự
 * ký được chữ ký hợp lệ, và tự cài lại HMAC trong test (không gọi code production).
 */
@Testcontainers
@SpringBootTest(properties = {
        "payment.vnpay.tmn-code=TMN001",
        "payment.vnpay.hash-secret=VNPAYSECRET123"
})
@AutoConfigureMockMvc
class PaymentCallbackControllerIT {

    private static final String VNPAY_CALLBACK = "/api/billing/payments/callback/vnpay";
    private static final String VNPAY_RETURN = "/api/billing/payments/return/vnpay";
    /** Đường IPN của MoMo trước khi gỡ cổng (MoMo nằm ngoài đề cương). */
    private static final String REMOVED_MOMO_CALLBACK = "/api/billing/payments/callback/momo";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    private static String hmacSha512Hex(String secret, String data) {
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

    /** Dựng query string VNPay có chữ ký ĐÚNG cho một orderId không tồn tại trong DB. */
    private String signedVnpayQuery(String orderId) {
        var params = new TreeMap<String, String>();
        params.put("vnp_Amount", "600000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "TMN001");
        params.put("vnp_TxnRef", orderId);
        var hashData = params.entrySet().stream()
                .map(entry -> entry.getKey() + "="
                        + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElseThrow();
        var plainQuery = params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right).orElseThrow();
        return plainQuery + "&vnp_SecureHash=" + hmacSha512Hex("VNPAYSECRET123", hashData);
    }

    /** IPN và trang Return URL phải gọi được KHÔNG kèm cookie đăng nhập và KHÔNG kèm CSRF token - cổng
     * gọi server-to-server, còn người trả có thể chưa đăng nhập khi quay về (spec mục 5, 10). */
    @Test
    void vnpayIpnAndReturnEndpointsArePublicAndCsrfExempt() throws Exception {
        var ipn = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-public-check"))).andReturn();
        var returned = mockMvc.perform(get(VNPAY_RETURN + "?" + signedVnpayQuery("order-public-check"))).andReturn();

        assertThat(ipn.getResponse().getStatus()).isNotIn(401, 403);
        assertThat(returned.getResponse().getStatus()).isNotIn(401, 403);
    }

    /**
     * Review Focus #5, VNPay: chữ ký sai và orderId lạ (chữ ký đúng) trả về status + body GIỐNG NHAU
     * từng byte - không có cách nào từ ngoài biết orderId nào tồn tại.
     */
    @Test
    void vnpayReturnsAnIdenticalResponseForABadSignatureAndForAnUnknownOrderId() throws Exception {
        var badSignature = mockMvc.perform(get(VNPAY_CALLBACK
                + "?vnp_Amount=600000000&vnp_ResponseCode=00&vnp_TmnCode=TMN001&vnp_TxnRef=order-x"
                + "&vnp_SecureHash=deadbeef")).andReturn().getResponse();
        var unknownOrder = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-does-not-exist")))
                .andReturn().getResponse();

        assertThat(badSignature.getStatus()).isEqualTo(unknownOrder.getStatus());
        assertThat(badSignature.getContentAsString()).isEqualTo(unknownOrder.getContentAsString());
        assertThat(badSignature.getContentAsString()).doesNotContain("order-x", "SIGNATURE", "signature");
    }

    /** Body cố định của VNPay đúng theo hợp đồng IPN của họ - trả khác đi là VNPay coi như chưa nhận. */
    @Test
    void vnpayAlwaysAnswersWithTheFixedConfirmationBody() throws Exception {
        var response = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-fixed-body")))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
    }

    /** Trang Return URL không phải đường vào của cổng: chữ ký sai trả thẳng 400 cho người dùng thấy. */
    @Test
    void vnpayReturnRejectsATamperedQueryWithBadRequest() throws Exception {
        var response = mockMvc.perform(get(VNPAY_RETURN
                + "?vnp_Amount=600000000&vnp_ResponseCode=00&vnp_TmnCode=TMN001&vnp_TxnRef=order-z"
                + "&vnp_SecureHash=deadbeef")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("BILLING_INVALID_CALLBACK_SIGNATURE");
    }

    /** MoMo đã gỡ: đường IPN cũ không còn xử lý gì (không public, không controller). */
    @Test
    void theRemovedMomoCallbackNoLongerAcceptsAnything() throws Exception {
        var response = mockMvc.perform(post(REMOVED_MOMO_CALLBACK).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"order-momo\"}")).andReturn().getResponse();

        assertThat(response.getStatus()).isGreaterThanOrEqualTo(400);
    }

    /** Endpoint tra trạng thái cũng public (trang Return URL chạy khi phụ huynh chưa đăng nhập), và
     * một gatewayTransactionId lạ trả 404 ProblemDetail bình thường - endpoint này KHÔNG phải đường
     * vào của cổng thanh toán nên không áp quy tắc "một response duy nhất". */
    @Test
    void paymentStatusIsPublicAndReportsAnUnknownTransactionAsNotFound() throws Exception {
        var response = mockMvc.perform(get("/api/billing/payments/order-not-there/status")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("BILLING_PAYMENT_NOT_FOUND");
    }
}
