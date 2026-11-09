package com.eduerp.modules.payroll;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** Tháng chưa hết thì giờ dạy chưa đủ: duyệt lúc này sẽ khoá một con số còn thiếu. */
public final class PayrollPeriodNotEndedException extends PayrollException {
    public PayrollPeriodNotEndedException(LocalDate periodEnd) {
        super("PAYROLL_PERIOD_NOT_ENDED", HttpStatus.CONFLICT,
                "Kỳ lương kết thúc vào " + periodEnd + ", chỉ gửi duyệt được sau ngày đó");
    }
}
