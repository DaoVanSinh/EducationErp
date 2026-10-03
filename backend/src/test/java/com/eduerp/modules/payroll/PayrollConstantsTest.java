package com.eduerp.modules.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollConstantsTest {

    @Test
    void statutoryRatesSumToThirtyTwoPercent() {
        var total = PayrollConstants.StatutoryRates.EMPLOYER_SHARE.add(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE);
        assertThat(total).isEqualTo(new BigDecimal("0.320"));
    }

    @Test
    void probationRateIsEightyFivePercent() {
        assertThat(PayrollConstants.StatutoryRates.PROBATION_RATE).isEqualTo(new BigDecimal("0.85"));
    }

    @Test
    void contractTypesHaveExactlyTwoValues() {
        assertThat(PayrollConstants.ContractType.values())
                .containsExactly(PayrollConstants.ContractType.OFFICIAL, PayrollConstants.ContractType.COLLABORATOR);
    }

    @Test
    void payrollRunStatusTransitionsAreDefinedInOrder() {
        assertThat(PayrollConstants.PayrollRunStatus.values()).containsExactly(
                PayrollConstants.PayrollRunStatus.DRAFT, PayrollConstants.PayrollRunStatus.PENDING_APPROVAL,
                PayrollConstants.PayrollRunStatus.APPROVED);
    }
}
