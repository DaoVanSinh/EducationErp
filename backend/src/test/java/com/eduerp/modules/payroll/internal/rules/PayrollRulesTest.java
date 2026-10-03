package com.eduerp.modules.payroll.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollRulesTest {

    /** Review Focus #1: probationEndDate rơi giữa kỳ lương (15/1, kỳ chốt là tháng 1) vẫn tính
     * NGUYÊN THÁNG theo trạng thái tại ngày đầu kỳ (1/1, vẫn trong thử việc) - không chia theo ngày. */
    @Test
    void baseGrossPayUsesWholeMonthProbationRateWhenProbationEndsMidPeriod() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), BigDecimal.ZERO);

        assertThat(grossPay).isEqualTo(new BigDecimal("10000000").multiply(new BigDecimal("0.85")));
    }

    @Test
    void baseGrossPayIsFullSalaryWhenNotInProbationAtPeriodStart() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), BigDecimal.ZERO);

        assertThat(grossPay).isEqualTo(new BigDecimal("10000000"));
    }

    @Test
    void baseGrossPayForCollaboratorIsHourlyRateTimesHoursWorked() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), new BigDecimal("20"));

        assertThat(grossPay).isEqualTo(new BigDecimal("150000").multiply(new BigDecimal("20")));
    }

    @Test
    void isInProbationIsFalseWhenNoProbationDatesSet() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());

        assertThat(PayrollRules.isInProbation(contract, LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void totalAllowancesSumsAllAmounts() {
        var allowances = List.of(new ContractAllowance("Xăng xe", new BigDecimal("500000")),
                new ContractAllowance("Ăn trưa", new BigDecimal("300000")));

        assertThat(PayrollRules.totalAllowances(allowances)).isEqualTo(new BigDecimal("800000"));
    }

    @Test
    void socialInsuranceAppliesOnlyToOfficialContracts() {
        var grossPay = new BigDecimal("10000000");

        assertThat(PayrollRules.socialInsuranceEmployeeShare(grossPay, PayrollConstants.ContractType.OFFICIAL))
                .isEqualTo(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE));
        assertThat(PayrollRules.socialInsuranceEmployerShare(grossPay, PayrollConstants.ContractType.OFFICIAL))
                .isEqualTo(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYER_SHARE));
        assertThat(PayrollRules.socialInsuranceEmployeeShare(grossPay, PayrollConstants.ContractType.COLLABORATOR))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(PayrollRules.socialInsuranceEmployerShare(grossPay, PayrollConstants.ContractType.COLLABORATOR))
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void netPayAddsAllowancesAndSubtractsInsuranceAndTax() {
        var netPay = PayrollRules.netPay(new BigDecimal("10000000"), new BigDecimal("500000"),
                new BigDecimal("1050000"), new BigDecimal("200000"));

        assertThat(netPay).isEqualTo(new BigDecimal("10000000").add(new BigDecimal("500000"))
                .subtract(new BigDecimal("1050000")).subtract(new BigDecimal("200000")));
    }
}
