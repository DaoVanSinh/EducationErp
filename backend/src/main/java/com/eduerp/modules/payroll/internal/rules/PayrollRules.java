package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Pure, 100% unit-test được bằng new, không I/O - mirror tinh thần internal/rules của các module khác. */
public final class PayrollRules {

    private PayrollRules() {
    }

    /**
     * Review Focus #1: {@code payPeriodStart} luôn là ngày đầu kỳ lương (year-month-01) - trạng thái
     * thử việc được chốt TẠI ngày này cho NGUYÊN cả kỳ, không chia theo ngày trong tháng dù
     * probationEndDate rơi giữa kỳ (quyết định tại plan, spec mục 6.1).
     */
    public static BigDecimal baseGrossPay(EmploymentContract contract, LocalDate payPeriodStart,
            BigDecimal hoursWorked) {
        if (contract.getContractType() == PayrollConstants.ContractType.OFFICIAL) {
            var base = contract.getBaseSalary();
            return isInProbation(contract, payPeriodStart) ? base.multiply(PayrollConstants.StatutoryRates.PROBATION_RATE)
                    : base;
        }
        return contract.getHourlyRate().multiply(hoursWorked);
    }

    public static boolean isInProbation(EmploymentContract contract, LocalDate asOf) {
        var start = contract.getProbationStartDate();
        var end = contract.getProbationEndDate();
        if (start == null || end == null) {
            return false;
        }
        return !asOf.isBefore(start) && !asOf.isAfter(end);
    }

    public static BigDecimal totalAllowances(List<ContractAllowance> allowances) {
        return allowances.stream().map(ContractAllowance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal socialInsuranceEmployeeShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE) : BigDecimal.ZERO;
    }

    public static BigDecimal socialInsuranceEmployerShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYER_SHARE) : BigDecimal.ZERO;
    }

    public static BigDecimal netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal socialInsuranceEmployeeShare,
            BigDecimal incomeTaxWithheld) {
        return grossPay.add(allowances).subtract(socialInsuranceEmployeeShare).subtract(incomeTaxWithheld);
    }
}
