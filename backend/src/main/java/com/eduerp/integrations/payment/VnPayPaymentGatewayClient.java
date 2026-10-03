package com.eduerp.integrations.payment;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * VNPay 2.1.0 (spec mục 2). Công thức chốt: sắp key theo alphabet → ghép
 * {@code key=URLEncoder.encode(value)} bằng {@code &} → HMAC-SHA512 với {@code vnp_HashSecret} → hex
 * chữ thường. Chuỗi ký và chuỗi query gửi đi là cùng một chuỗi, nên không thể lệch encode.
 */
@Component
class VnPayPaymentGatewayClient implements PaymentGatewayClient {

    /** Tên tham số của VNPay - chuỗi chỉ dùng trong class này nên gom vào inner class tại đây. */
    private static final class Params {
        private Params() {
        }

        static final String VERSION = "vnp_Version";
        static final String COMMAND = "vnp_Command";
        static final String TMN_CODE = "vnp_TmnCode";
        static final String AMOUNT = "vnp_Amount";
        static final String CURR_CODE = "vnp_CurrCode";
        static final String TXN_REF = "vnp_TxnRef";
        static final String ORDER_INFO = "vnp_OrderInfo";
        static final String ORDER_TYPE = "vnp_OrderType";
        static final String LOCALE = "vnp_Locale";
        static final String RETURN_URL = "vnp_ReturnUrl";
        static final String IP_ADDR = "vnp_IpAddr";
        static final String CREATE_DATE = "vnp_CreateDate";
        static final String SECURE_HASH = "vnp_SecureHash";
        static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
        static final String RESPONSE_CODE = "vnp_ResponseCode";
    }

    private static final class Values {
        private Values() {
        }

        static final String VERSION = "2.1.0";
        static final String COMMAND = "pay";
        static final String CURRENCY = "VND";
        static final String ORDER_TYPE = "other";
        static final String LOCALE = "vn";
        static final String SUCCESS_RESPONSE_CODE = "00";
        static final String HMAC_ALGORITHM = "HmacSHA512";
        /** VNPay ghi nhận IP của máy gọi API. {@code PaymentRequest} (hợp đồng chốt ở spec mục 6)
         * không mang IP người trả tiền, nên dùng IP loopback của server - VNPay chỉ log, không dùng
         * giá trị này để chặn giao dịch. */
        static final String SERVER_IP = "127.0.0.1";
        /** VNPay đếm theo đơn vị xu, nên mọi số tiền VND phải nhân 100 trước khi ký. */
        static final BigDecimal AMOUNT_MULTIPLIER = new BigDecimal("100");
    }

    private static final DateTimeFormatter CREATE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties properties;
    private final Clock clock;

    @Autowired
    VnPayPaymentGatewayClient(VnPayProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /** Constructor cho test: {@code vnp_CreateDate} phụ thuộc giờ nên phải chốt được Clock. */
    VnPayPaymentGatewayClient(VnPayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.VNPAY;
    }

    @Override
    public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
        var returnUrl = request.returnUrl() == null || request.returnUrl().isBlank()
                ? properties.returnUrl() : request.returnUrl();
        SortedMap<String, String> params = new TreeMap<>();
        params.put(Params.VERSION, Values.VERSION);
        params.put(Params.COMMAND, Values.COMMAND);
        params.put(Params.TMN_CODE, properties.tmnCode());
        params.put(Params.AMOUNT, request.amount().multiply(Values.AMOUNT_MULTIPLIER).toBigInteger().toString());
        params.put(Params.CURR_CODE, Values.CURRENCY);
        params.put(Params.TXN_REF, request.orderId());
        params.put(Params.ORDER_INFO, request.orderInfo());
        params.put(Params.ORDER_TYPE, Values.ORDER_TYPE);
        params.put(Params.LOCALE, Values.LOCALE);
        params.put(Params.RETURN_URL, returnUrl);
        params.put(Params.IP_ADDR, Values.SERVER_IP);
        params.put(Params.CREATE_DATE, LocalDateTime.now(clock).format(CREATE_DATE_FORMAT));

        var hashData = hashData(params);
        var secureHash = hmacSha512Hex(properties.hashSecret(), hashData);
        return new PaymentUrlResult(
                properties.payUrl() + "?" + hashData + "&" + Params.SECURE_HASH + "=" + secureHash,
                request.orderId());
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
        var received = rawParams.get(Params.SECURE_HASH);
        SortedMap<String, String> signed = new TreeMap<>(rawParams);
        signed.remove(Params.SECURE_HASH);
        signed.remove(Params.SECURE_HASH_TYPE);
        var expected = hmacSha512Hex(properties.hashSecret(), hashData(signed));
        if (received == null || !expected.equalsIgnoreCase(received)) {
            throw new PaymentSignatureException(PaymentGatewayType.VNPAY);
        }
        var responseCode = rawParams.get(Params.RESPONSE_CODE);
        var amountInCents = rawParams.get(Params.AMOUNT);
        var amount = amountInCents == null ? BigDecimal.ZERO
                : new BigDecimal(amountInCents).divide(Values.AMOUNT_MULTIPLIER);
        return new PaymentCallbackResult(rawParams.get(Params.TXN_REF),
                Values.SUCCESS_RESPONSE_CODE.equals(responseCode), amount,
                Params.RESPONSE_CODE + "=" + responseCode);
    }

    /** Sắp key theo alphabet (TreeMap), ghép {@code key=URLEncoder.encode(value)} bằng {@code &}. */
    static String hashData(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    static String hmacSha512Hex(String secret, String data) {
        try {
            var mac = Mac.getInstance(Values.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Values.HMAC_ALGORITHM));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // Thuật toán HmacSHA512 luôn có trong JDK - lỗi ở đây là cấu hình JVM sai, không phải
            // tình huống nghiệp vụ, nên để nó nổ ra thay vì âm thầm trả chữ ký rỗng.
            throw new IllegalStateException("Không khởi tạo được " + Values.HMAC_ALGORITHM, e);
        }
    }

    private static String toHex(byte[] bytes) {
        var hex = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
