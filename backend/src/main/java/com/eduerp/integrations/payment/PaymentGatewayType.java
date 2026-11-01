package com.eduerp.integrations.payment;

/**
 * Cổng thanh toán online đang tích hợp. Chỉ còn VNPay: MoMo đã gỡ vì nằm ngoài đề cương ĐATN
 * (giao dịch MoMo cũ vẫn hiển thị qua {@code BillingConstants.PaymentMethod.MOMO}, chỉ không tạo mới).
 */
public enum PaymentGatewayType {
    VNPAY
}
