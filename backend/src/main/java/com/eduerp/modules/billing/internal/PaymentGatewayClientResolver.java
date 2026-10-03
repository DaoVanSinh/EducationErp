package com.eduerp.modules.billing.internal;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.UnknownPaymentGatewayException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cầu nối duy nhất giữa từ vựng nghiệp vụ ({@code BillingConstants.PaymentMethod}) và từ vựng hạ
 * tầng ({@code PaymentGatewayType}). Spring đưa vào mọi {@code PaymentGatewayClient} có trong
 * context; {@code MANUAL} không phải cổng nào nên luôn bị từ chối (spec mục 6).
 *
 * <p>Map bằng {@code name()} chứ không bằng một bảng viết tay - {@code BillingConstantsTest} chốt
 * rằng hai enum trùng tên, nên một cổng mới thêm vào là tự động hoạt động.
 */
@Component
public class PaymentGatewayClientResolver {

    private final Map<PaymentGatewayType, PaymentGatewayClient> clientsByType =
            new EnumMap<>(PaymentGatewayType.class);

    PaymentGatewayClientResolver(List<PaymentGatewayClient> clients) {
        clients.forEach(client -> clientsByType.put(client.type(), client));
    }

    public PaymentGatewayClient resolve(BillingConstants.PaymentMethod method) {
        var client = clientsByType.get(toGatewayType(method));
        if (client == null) {
            throw new UnknownPaymentGatewayException(method);
        }
        return client;
    }

    /** Dùng khi đã biết cổng từ chính đường dẫn callback, không qua từ vựng nghiệp vụ. */
    public PaymentGatewayClient resolve(PaymentGatewayType type) {
        var client = clientsByType.get(type);
        if (client == null) {
            throw new UnknownPaymentGatewayException(BillingConstants.PaymentMethod.valueOf(type.name()));
        }
        return client;
    }

    private static PaymentGatewayType toGatewayType(BillingConstants.PaymentMethod method) {
        try {
            return PaymentGatewayType.valueOf(method.name());
        } catch (IllegalArgumentException notAGateway) {
            // MANUAL (hoặc một method tương lai không có cổng tương ứng) - trả null để nhánh kiểm
            // tra bên trên ném đúng lỗi nghiệp vụ thay vì để IllegalArgumentException lọt ra HTTP.
            return null;
        }
    }
}
