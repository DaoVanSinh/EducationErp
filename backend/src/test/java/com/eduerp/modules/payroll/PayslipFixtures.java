package com.eduerp.modules.payroll;

import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.rules.PayslipFigures;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import java.math.BigDecimal;
import java.util.UUID;

/** Dựng phiếu lương cho test mà không phải liệt kê mọi con số của công thức. */
public final class PayslipFixtures {

    private PayslipFixtures() {
    }

    /** Lương tháng đủ 30 ngày: BHXH 10,5% và 21,5%, không thuế. */
    public static Payslip monthly(PayrollRun run, UUID accountId, long grossPay) {
        var gross = BigDecimal.valueOf(grossPay);
        return new Payslip(run, accountId, new PayslipFigures(PayType.MONTHLY, gross, 0, BigDecimal.ZERO, 30, 0, 0,
                gross, BigDecimal.ZERO, gross.multiply(new BigDecimal("0.105")),
                gross.multiply(new BigDecimal("0.215")), BigDecimal.ZERO));
    }

    /** Trả theo giờ, không BHXH, không thuế. */
    public static Payslip hourly(PayrollRun run, UUID accountId, long grossPay, long hours) {
        var gross = BigDecimal.valueOf(grossPay);
        return new Payslip(run, accountId, new PayslipFigures(PayType.HOURLY, gross, 1, BigDecimal.valueOf(hours), 0,
                0, 0, gross, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
