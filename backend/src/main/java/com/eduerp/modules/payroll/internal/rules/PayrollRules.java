package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Pure, 100% unit-test được bằng new, không I/O - mirror tinh thần internal/rules của các module khác. */
public final class PayrollRules {

    /** Mọi cột tiền trong DB là NUMERIC(14,2) - số trả về client phải khớp số sẽ được lưu xuống, nếu
     * không để Postgres tự làm tròn lúc ghi thì con số người dùng thấy lúc nhập lệch với con số đối
     * soát sau khi tải lại (review finding Important #7). */
    private static final int MONEY_SCALE = 2;

    private PayrollRules() {
    }

    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
            return isInProbation(contract, payPeriodStart)
                    ? round(base.multiply(PayrollConstants.StatutoryRates.PROBATION_RATE)) : round(base);
        }
        return round(contract.getHourlyRate().multiply(hoursWorked));
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
        return round(allowances.stream().map(ContractAllowance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static BigDecimal socialInsuranceEmployeeShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? round(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE)) : BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    public static BigDecimal socialInsuranceEmployerShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? round(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYER_SHARE)) : BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    public static BigDecimal netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal socialInsuranceEmployeeShare,
            BigDecimal incomeTaxWithheld) {
        return round(grossPay.add(allowances).subtract(socialInsuranceEmployeeShare).subtract(incomeTaxWithheld));
    }
}
