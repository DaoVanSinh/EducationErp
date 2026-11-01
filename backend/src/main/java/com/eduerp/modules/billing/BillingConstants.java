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

    /** {@code VNPAY} trùng tên {@code integrations.payment.PaymentGatewayType} - đó là cách
     * {@code PaymentGatewayClientResolver} map hai enum ({@code BillingConstantsTest} chốt lại). Các
     * giá trị không có cổng tương ứng (MOMO cũ, thu tay) bị resolver từ chối bằng
     * {@code UnknownPaymentGatewayException}. */
    public enum PaymentMethod {
        /** Ví MoMo - đã gỡ cổng (ngoài đề cương ĐATN); chỉ còn ở dữ liệu cũ, không tạo giao dịch mới. */
        MOMO,
        VNPAY,
        /** Thu tay trước khi tách tiền mặt/chuyển khoản - chỉ còn ở dữ liệu cũ, không ghi mới nữa. */
        MANUAL,
        /** Tiền mặt thu tại quầy. */
        CASH,
        /** Chuyển khoản ngân hàng do kế toán đối chiếu sao kê rồi ghi nhận. */
        BANK_TRANSFER
    }

    /**
     * Kênh thu tay mà kế toán được chọn khi ghi nhận - tách khỏi {@link PaymentMethod} để request
     * thu tay không thể gửi nhầm {@code VNPAY}/{@code MOMO} (Jackson trả 400 cho giá trị lạ), và để
     * {@code MANUAL} cũ không được ghi mới.
     */
    public enum OfflinePaymentMethod {
        CASH, BANK_TRANSFER;

        public PaymentMethod toPaymentMethod() {
            return PaymentMethod.valueOf(name());
        }
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

        /** Spec mục 1: 3 đợt cho CẢ combo, KHÔNG phải 3 đợt mỗi khoá trong combo. Để riêng khỏi
         * {@link #MAX_INSTALLMENTS_PER_ENROLLMENT} vì đó là hai quyết định nghiệp vụ độc lập: đổi
         * một cái không được kéo theo cái kia. */
        public static final int MAX_INSTALLMENTS_PER_COMBO = 3;

        /** Spec mục 6 bước 1: dưới mốc này thì đó không phải combo, chỉ là một ghi danh. */
        public static final int MIN_ENROLLMENTS_PER_COMBO = 2;

        /** Đăng ký nhóm: từ 2 học viên cùng học một khoá. Dùng chung bảng bậc giảm giá với combo, nên mốc
         * thấp nhất trùng với combo, nhưng là một quyết định riêng. */
        public static final int MIN_MEMBERS_PER_GROUP = 2;

        /** Khớp cột {@code payments.note VARCHAR(255)} (V35): số phiếu thu, nội dung chuyển khoản... */
        public static final int MAX_PAYMENT_NOTE_LENGTH = 255;
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
