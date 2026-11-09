package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

/** Duyệt kỳ lương khi còn người đã dạy mà chưa có mức lương là bỏ sót lương của họ cả tháng đó. */
public final class PayrollRunMissingPayTermsException extends PayrollException {
    public PayrollRunMissingPayTermsException(long teacherCount) {
        super("PAYROLL_MISSING_PAY_TERMS", HttpStatus.CONFLICT,
                "Còn " + teacherCount + " giáo viên có buổi dạy trong kỳ nhưng chưa thiết lập lương");
    }
}
