package com.eduerp.modules.payroll;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hằng số dùng chung của module payroll, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}. Công thức và căn cứ pháp lý:
 * {@code docs/superpowers/specs/2026-11-05-luong-giao-vien-design.md}.
 */
public final class PayrollConstants {

    private PayrollConstants() {
    }

    public enum PayrollRunStatus {
        DRAFT, PENDING_APPROVAL, APPROVED
    }

    /** Lương trả đủ một lần, bằng tiền mặt hoặc chuyển khoản. */
    public enum PaymentMethod {
        CASH, BANK_TRANSFER
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_NOTE_LENGTH = 1000;

        /** Khớp cột {@code payslips.payment_note VARCHAR(500)} (V40). */
        public static final int MAX_PAYMENT_NOTE_LENGTH = 500;
    }

    public static final class Schedules {
        private Schedules() {
        }

        /** "Hôm nay" của trung tâm khi xét kỳ lương đã kết thúc hay chưa. */
        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }

    /**
     * BHXH/BHYT/BHTN, chỉ áp dụng giáo viên trả theo tháng. Hằng số Java, không phải bảng DB - mỗi lần đổi là
     * thay đổi luật cần deploy lại.
     */
    public static final class StatutoryRates {
        private StatutoryRates() {
        }

        public static final BigDecimal EMPLOYER_SHARE = new BigDecimal("0.215");
        public static final BigDecimal EMPLOYEE_SHARE = new BigDecimal("0.105");
        public static final BigDecimal PROBATION_RATE = new BigDecimal("0.85");
        /** 20 lần mức tham chiếu 2.530.000 đ, từ 01/07/2026. */
        public static final BigDecimal SOCIAL_INSURANCE_SALARY_CAP = new BigDecimal("50600000");
    }

    /** Thuế TNCN từ tiền lương, theo tháng. */
    public static final class IncomeTax {
        private IncomeTax() {
        }

        /** Một bậc thuế: phần thu nhập tới {@code upperBound} chịu {@code rate}; null = không giới hạn trên. */
        public record Bracket(BigDecimal upperBound, BigDecimal rate) {
        }

        /** Luật Thuế TNCN 109/2025/QH15, Điều 9, từ kỳ tính thuế 2026. */
        public static final List<Bracket> BRACKETS = List.of(
                new Bracket(new BigDecimal("10000000"), new BigDecimal("0.05")),
                new Bracket(new BigDecimal("30000000"), new BigDecimal("0.10")),
                new Bracket(new BigDecimal("60000000"), new BigDecimal("0.20")),
                new Bracket(new BigDecimal("100000000"), new BigDecimal("0.30")),
                new Bracket(null, new BigDecimal("0.35")));

        /** Nghị quyết 110/2025/UBTVQH15. */
        public static final BigDecimal PERSONAL_DEDUCTION = new BigDecimal("15500000");
        public static final BigDecimal DEPENDENT_DEDUCTION = new BigDecimal("6200000");

        /** Khấu trừ tại nguồn của người trả theo giờ: Nghị định 253/2026/NĐ-CP, từ 01/07/2026. */
        public static final BigDecimal HOURLY_WITHHOLDING_RATE = new BigDecimal("0.10");
        public static final BigDecimal HOURLY_WITHHOLDING_THRESHOLD = new BigDecimal("5000000");
    }

    /** Không cần cache namespace nào ở V1 (không có read nóng cần cache) - để trống theo khuôn tier 0. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
