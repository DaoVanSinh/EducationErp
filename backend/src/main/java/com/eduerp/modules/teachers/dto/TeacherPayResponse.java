package com.eduerp.modules.teachers.dto;

import com.eduerp.modules.teachers.TeachersConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Điều khoản lương của một giáo viên, tách khỏi {@link TeacherProfileResponse}: quyền đọc giáo viên rộng hơn
 * quyền xem lương. {@code payType} null = chưa thiết lập.
 */
public record TeacherPayResponse(UUID profileId, TeachersConstants.PayType payType, BigDecimal monthlySalary,
        BigDecimal hourlyRate, LocalDate workStartDate, LocalDate probationEndDate, int dependentCount,
        List<AllowanceItem> allowances) {
}
