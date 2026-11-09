package com.eduerp.modules.payroll;

import java.time.YearMonth;
import org.springframework.http.HttpStatus;

/** Tháng chưa bắt đầu thì chưa có gì để tính lương. */
public final class PayrollPeriodInFutureException extends PayrollException {
    public PayrollPeriodInFutureException(YearMonth period) {
        super("PAYROLL_PERIOD_IN_FUTURE", HttpStatus.BAD_REQUEST,
                "Tháng " + period.getMonthValue() + "/" + period.getYear() + " chưa bắt đầu, chưa tạo được kỳ lương");
    }
}
