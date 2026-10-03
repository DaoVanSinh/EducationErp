package com.eduerp.integrations.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MoMo One-Time Payment v2 (spec mục 2). Tạo link: POST JSON tới {@code /v2/gateway/api/create},
 * {@code signature} = HMAC-SHA256 của chuỗi khoá theo thứ tự CỐ ĐỊNH ở {@link #createRequestPayloadToSign}
 * (không url-encode), ký bằng {@code secretKey}; response trả {@code payUrl}.
 *
 * <p>IPN: MoMo POST JSON tới {@code ipnUrl}. Payload IPN không có {@code ipnUrl}/{@code redirectUrl}/
 * {@code requestType} nên công thức tạo link không áp được - bộ khoá IPN thật của MoMo nằm ở
 * {@link #ipnPayloadToSign} (xem ghi chú trong plan Task 10). {@code resultCode=0} là thành công.
 */
@Component
class MomoPaymentGatewayClient implements PaymentGatewayClient {

    private static final class Params {
        private Params() {
        }

        static final String PARTNER_CODE = "partnerCode";
        static final String ACCESS_KEY = "accessKey";
        static final String REQUEST_ID = "requestId";
        static final String AMOUNT = "amount";
        static final String ORDER_ID = "orderId";
        static final String ORDER_INFO = "orderInfo";
        static final String ORDER_TYPE = "orderType";
        static final String REDIRECT_URL = "redirectUrl";
        static final String IPN_URL = "ipnUrl";
        static final String REQUEST_TYPE = "requestType";
        static final String EXTRA_DATA = "extraData";
        static final String SIGNATURE = "signature";
        static final String PAY_URL = "payUrl";
        static final String RESULT_CODE = "resultCode";
        static final String MESSAGE = "message";
        static final String PAY_TYPE = "payType";
        static final String RESPONSE_TIME = "responseTime";
        static final String TRANS_ID = "transId";
        static final String LANG = "lang";
    }

    private static final class Values {
        private Values() {
        }

        static final String REQUEST_TYPE = "captureWallet";
        static final String LANG = "vi";
        static final String EXTRA_DATA = "";
        static final String SUCCESS_RESULT_CODE = "0";
        static final String HMAC_ALGORITHM = "HmacSHA256";
    }

    /** Thứ tự khoá của chuỗi ký IPN - alphabet theo đúng tài liệu MoMo, cố định trong một chỗ. */
    private static final List<String> IPN_SIGNED_KEYS = List.of(Params.ACCESS_KEY, Params.AMOUNT,
            Params.EXTRA_DATA, Params.MESSAGE, Params.ORDER_ID, Params.ORDER_INFO, Params.ORDER_TYPE,
            Params.PARTNER_CODE, Params.PAY_TYPE, Params.REQUEST_ID, Params.RESPONSE_TIME, Params.RESULT_CODE,
            Params.TRANS_ID);

    private final MomoProperties properties;
    private final RestClient restClient;

    MomoPaymentGatewayClient(MomoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.MOMO;
    }

    @Override
    public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
        var redirectUrl = request.returnUrl() == null || request.returnUrl().isBlank()
                ? properties.redirectUrl() : request.returnUrl();
        var ipnUrl = request.ipnUrl() == null || request.ipnUrl().isBlank()
                ? properties.ipnUrl() : request.ipnUrl();
        var requestId = UUID.randomUUID().toString();
        var amount = request.amount().toBigInteger().toString();
        var signature = hmacSha256Hex(properties.secretKey(), createRequestPayloadToSign(properties.accessKey(),
                amount, Values.EXTRA_DATA, ipnUrl, request.orderId(), request.orderInfo(),
                properties.partnerCode(), redirectUrl, requestId, Values.REQUEST_TYPE));

        var body = new LinkedHashMap<String, Object>();
        body.put(Params.PARTNER_CODE, properties.partnerCode());
        body.put(Params.ACCESS_KEY, properties.accessKey());
        body.put(Params.REQUEST_ID, requestId);
        // Số, không phải chuỗi: tài liệu MoMo v2 khai amount là kiểu số trong JSON body - chuỗi ký thì
        // vẫn ghép "amount=50000" như cũ, chữ ký không phụ thuộc kiểu JSON của body.
        body.put(Params.AMOUNT, Long.valueOf(amount));
        body.put(Params.ORDER_ID, request.orderId());
        body.put(Params.ORDER_INFO, request.orderInfo());
        body.put(Params.REDIRECT_URL, redirectUrl);
        body.put(Params.IPN_URL, ipnUrl);
        body.put(Params.EXTRA_DATA, Values.EXTRA_DATA);
        body.put(Params.REQUEST_TYPE, Values.REQUEST_TYPE);
        body.put(Params.LANG, Values.LANG);
        body.put(Params.SIGNATURE, signature);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(properties.endpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        return toPaymentUrlResult(request.orderId(), response);
    }

    /**
     * Final review Critical #1: MoMo báo lỗi nghiệp vụ (chữ ký sai, amount không hợp lệ, đối tác bị
     * khoá...) bằng HTTP 200 kèm {@code resultCode != 0} và KHÔNG có {@code payUrl}. Code cũ không đọc
     * {@code resultCode} của response tạo link (chỉ đọc ở {@code verifyCallback}), nên một lần bị MoMo
     * từ chối biến thành chuỗi {@code "null"} trả thẳng cho trình duyệt - không lỗi, không log, và một
     * {@code Payment} PENDING mồ côi vẫn được ghi ở tầng gọi (xem {@code InitiateOnlinePayment}).
     */
    private static PaymentUrlResult toPaymentUrlResult(String orderId, Map<String, Object> response) {
        if (response == null) {
            throw new PaymentGatewayException(PaymentGatewayType.MOMO, "Không nhận được phản hồi từ MoMo");
        }
        var resultCode = String.valueOf(response.get(Params.RESULT_CODE));
        if (!Values.SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw new PaymentGatewayException(PaymentGatewayType.MOMO,
                    "MoMo từ chối yêu cầu tạo link (resultCode=" + resultCode + "): " + response.get(Params.MESSAGE));
        }
        var payUrl = response.get(Params.PAY_URL);
        if (payUrl == null) {
            throw new PaymentGatewayException(PaymentGatewayType.MOMO, "MoMo không trả về payUrl dù resultCode=0");
        }
        return new PaymentUrlResult(String.valueOf(payUrl), orderId);
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
        var received = rawParams.get(Params.SIGNATURE);
        var expected = hmacSha256Hex(properties.secretKey(),
                ipnPayloadToSign(rawParams, properties.accessKey()));
        if (received == null || !expected.equalsIgnoreCase(received)) {
            throw new PaymentSignatureException(PaymentGatewayType.MOMO);
        }
        var amountValue = rawParams.get(Params.AMOUNT);
        var amount = amountValue == null || amountValue.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountValue);
        return new PaymentCallbackResult(rawParams.get(Params.ORDER_ID),
                Values.SUCCESS_RESULT_CODE.equals(rawParams.get(Params.RESULT_CODE)), amount,
                String.valueOf(rawParams.get(Params.MESSAGE)));
    }

    /** Thứ tự khoá nguyên văn theo spec mục 2 - KHÔNG url-encode, KHÔNG sắp lại. */
    static String createRequestPayloadToSign(String accessKey, String amount, String extraData, String ipnUrl,
            String orderId, String orderInfo, String partnerCode, String redirectUrl, String requestId,
            String requestType) {
        return Params.ACCESS_KEY + "=" + accessKey
                + "&" + Params.AMOUNT + "=" + amount
                + "&" + Params.EXTRA_DATA + "=" + extraData
                + "&" + Params.IPN_URL + "=" + ipnUrl
                + "&" + Params.ORDER_ID + "=" + orderId
                + "&" + Params.ORDER_INFO + "=" + orderInfo
                + "&" + Params.PARTNER_CODE + "=" + partnerCode
                + "&" + Params.REDIRECT_URL + "=" + redirectUrl
                + "&" + Params.REQUEST_ID + "=" + requestId
                + "&" + Params.REQUEST_TYPE + "=" + requestType;
    }

    /** Tham số thiếu thành chuỗi rỗng: đây là đường vào từ Internet, không được ném NPE. */
    static String ipnPayloadToSign(Map<String, String> params, String accessKey) {
        return IPN_SIGNED_KEYS.stream()
                .map(key -> key + "=" + resolveSignedValue(params, key, accessKey))
                .collect(Collectors.joining("&"));
    }

    private static String resolveSignedValue(Map<String, String> params, String key, String accessKey) {
        if (Params.ACCESS_KEY.equals(key)) {
            // MoMo không gửi accessKey trong IPN - nó nằm ở cấu hình phía mình.
            return accessKey;
        }
        var value = params.get(key);
        return value == null ? "" : value;
    }

    static String hmacSha256Hex(String secretKey, String payload) {
        try {
            var mac = Mac.getInstance(Values.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), Values.HMAC_ALGORITHM));
            var bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Không khởi tạo được " + Values.HMAC_ALGORITHM, e);
        }
    }
}
