package com.eduerp.modules.payroll;

import java.math.BigDecimal;

/**
 * Hằng số dùng chung của module payroll, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}.
 */
public final class PayrollConstants {

    private PayrollConstants() {
    }

    public enum ContractType {
        OFFICIAL, COLLABORATOR
    }

    public enum ContractStatus {
        ACTIVE, TERMINATED
    }

    public enum PayrollRunStatus {
        DRAFT, PENDING_APPROVAL, APPROVED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_ALLOWANCE_NAME_LENGTH = 100;
        public static final int MAX_ALLOWANCES_PER_CONTRACT = 20;
    }

    /**
     * BHXH/BHYT/BHTN (spec mục 2): tổng 32% lương tham gia BHXH, chỉ áp dụng hợp đồng OFFICIAL.
     * Hằng số Java, không phải bảng DB - tỷ lệ bắt buộc hiếm khi đổi, mỗi lần đổi là thay đổi luật
     * lớn cần deploy lại toàn hệ thống dù sao.
     */
    public static final class StatutoryRates {
        private StatutoryRates() {
        }

        public static final BigDecimal EMPLOYER_SHARE = new BigDecimal("0.215");
        public static final BigDecimal EMPLOYEE_SHARE = new BigDecimal("0.105");
        public static final BigDecimal PROBATION_RATE = new BigDecimal("0.85");
    }

    /** Không cần cache namespace nào ở V1 (không có read nóng cần cache) - để trống theo khuôn tier 0. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
