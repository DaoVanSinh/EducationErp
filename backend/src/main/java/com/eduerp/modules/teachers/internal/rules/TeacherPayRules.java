package com.eduerp.modules.teachers.internal.rules;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.teachers.TeachersConstants;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import java.math.BigDecimal;

/** Điều khoản lương phải khớp với hình thức trả lương: mọi trường được lưu đều đi vào công thức. */
public final class TeacherPayRules {

    private TeacherPayRules() {
    }

    public static void requireValid(TeacherPayTermsRequest terms) {
        if (terms.payType() == TeachersConstants.PayType.MONTHLY) {
            requireValidMonthly(terms);
            return;
        }
        requireValidHourly(terms);
    }

    private static void requireValidMonthly(TeacherPayTermsRequest terms) {
        requirePositive("monthlySalary", terms.monthlySalary(), "Nhập lương tháng lớn hơn 0");
        requireAbsent("hourlyRate", terms.hourlyRate() == null, "Giáo viên trả theo tháng không có đơn giá giờ");
        if (terms.probationEndDate() != null && terms.probationEndDate().isBefore(terms.workStartDate())) {
            throw new AppValidationException("probationEndDate", "Ngày hết thử việc phải sau ngày bắt đầu làm việc");
        }
    }

    private static void requireValidHourly(TeacherPayTermsRequest terms) {
        requirePositive("hourlyRate", terms.hourlyRate(), "Nhập đơn giá giờ lớn hơn 0");
        requireAbsent("monthlySalary", terms.monthlySalary() == null, "Giáo viên trả theo giờ không có lương tháng");
        requireAbsent("probationEndDate", terms.probationEndDate() == null, "Giáo viên trả theo giờ không có thử việc");
        requireAbsent("dependentCount", terms.dependentCount() == 0,
                "Giáo viên trả theo giờ khấu trừ thuế cố định, không khai người phụ thuộc");
        requireAbsent("allowances", terms.allowances().isEmpty(), "Giáo viên trả theo giờ không có phụ cấp");
    }

    private static void requirePositive(String field, BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new AppValidationException(field, message);
        }
    }

    private static void requireAbsent(String field, boolean absent, String message) {
        if (!absent) {
            throw new AppValidationException(field, message);
        }
    }
}
