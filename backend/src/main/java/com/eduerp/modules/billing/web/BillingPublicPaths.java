package com.eduerp.modules.billing.web;

/**
 * Ba đường dẫn public của module billing, khai ở MỘT chỗ để {@link BillingSecurityConfig} và các
 * {@code @RequestMapping} cùng tham chiếu thay vì chép lại chuỗi (không hardcode path trong config).
 * Package-private: chỉ module billing cần biết, KHÔNG expose ra ngoài - nếu để
 * {@code modules.identity} đọc thì sinh vòng {@code identity → billing → courses → identity}.
 *
 * <p>Hai callback public vì cổng thanh toán gọi server-to-server, không có session/cookie của ứng
 * dụng; xác thực đi bằng chữ ký HMAC trong usecase, không bằng Spring Security.
 * {@link #PAYMENT_STATUS_PATTERN} public vì trang {@code /payment/return/:gateway} chạy khi phụ
 * huynh chưa đăng nhập (spec mục 10); {@code gatewayTransactionId} là chuỗi không đoán được nên đóng
 * vai capability token, và {@code PaymentResponse} không chứa dữ liệu cá nhân nào.
 */
final class BillingPublicPaths {

    private BillingPublicPaths() {
    }

    static final String VNPAY_CALLBACK = "/api/billing/payments/callback/vnpay";
    static final String MOMO_CALLBACK = "/api/billing/payments/callback/momo";
    static final String PAYMENT_STATUS_PATTERN = "/api/billing/payments/*/status";

    /** Truyền thẳng vào {@code securityMatcher(...)}. */
    static final String[] ALL = {VNPAY_CALLBACK, MOMO_CALLBACK, PAYMENT_STATUS_PATTERN};
}
