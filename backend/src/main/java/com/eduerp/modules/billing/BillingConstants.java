package com.eduerp.modules.billing;

/**
 * Hằng số dùng chung của module billing, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code PayrollConstants}.
 */
public final class BillingConstants {

    private BillingConstants() {
    }

    public enum InvoiceStatus {
        UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
    }

    /** {@code MOMO}/{@code VNPAY} trùng tên {@code integrations.payment.PaymentGatewayType} - đó là
     * cách {@code PaymentGatewayClientResolver} map hai enum ({@code BillingConstantsTest} chốt lại). */
    public enum PaymentMethod {
        MOMO, VNPAY, MANUAL
    }

    /** {@code REJECTED} (final review Critical #2): cổng xác nhận thành công bằng chữ ký hợp lệ,
     * nhưng hoá đơn không còn ở trạng thái nhận tiền được (đã huỷ/đã trả đủ) lúc callback tới - tiền
     * có thật nhưng KHÔNG được tự cộng vào một hoá đơn đã chốt, cần kế toán đối soát thủ công. Khác
     * {@code FAILED}: đó là cổng tự báo giao dịch thất bại, còn đây là hệ thống từ chối một giao dịch
     * mà cổng nói là thành công. */
    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED, REJECTED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_INSTALLMENTS_PER_ENROLLMENT = 3;
    }

    /** Cron phải là hằng biên dịch để nhét được vào {@code @Scheduled} - không hardcode trong annotation. */
    public static final class Schedules {
        private Schedules() {
        }

        /** 1:00 sáng mỗi ngày (spec mục 5). */
        public static final String MARK_OVERDUE_CRON = "0 0 1 * * *";
    }

    /** Nội dung hiển thị trên cổng thanh toán - người trả tiền thấy chuỗi này trên app MoMo/VNPay. */
    public static final class OrderInfo {
        private OrderInfo() {
        }

        public static final String PREFIX = "Hoc phi dot ";
    }

    /** Không cần cache namespace nào ở V1 - để trống theo khuôn tier 0 của PayrollConstants. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
