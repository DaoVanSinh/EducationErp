package com.eduerp.modules.teachers.dto;

import com.eduerp.modules.teachers.TeachersConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Điều khoản lương. Trường nào dùng cho hình thức nào do {@code TeacherPayRules} kiểm tra: trả theo tháng cần
 * {@code monthlySalary}, trả theo giờ cần {@code hourlyRate} và không được có thử việc, người phụ thuộc, phụ cấp.
 */
public record TeacherPayTermsRequest(@NotNull TeachersConstants.PayType payType, BigDecimal monthlySalary,
        BigDecimal hourlyRate, @NotNull LocalDate workStartDate, LocalDate probationEndDate,
        @Min(0) @Max(TeachersConstants.Limits.MAX_DEPENDENTS) int dependentCount,
        @NotNull @Size(max = TeachersConstants.Limits.MAX_ALLOWANCES) List<@Valid @NotNull AllowanceItem> allowances) {
}
