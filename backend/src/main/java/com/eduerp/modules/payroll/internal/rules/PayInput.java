package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.teachers.TeachersConstants;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Đầu vào của công thức lương cho một giáo viên trong một kỳ. {@code lastWorkDate} null = còn làm việc. */
public record PayInput(TeachersConstants.PayType payType, BigDecimal monthlySalary, BigDecimal hourlyRate,
        LocalDate workStartDate, LocalDate probationEndDate, LocalDate lastWorkDate, int dependentCount,
        BigDecimal allowancesTotal, int sessionCount, int totalMinutes, BigDecimal adjustment) {
}
