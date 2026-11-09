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
    void taxBracketsAreOrderedAndOnlyTheLastOneIsOpenEnded() {
        var brackets = PayrollConstants.IncomeTax.BRACKETS;

        assertThat(brackets).hasSize(5);
        assertThat(brackets.get(brackets.size() - 1).upperBound()).isNull();
        for (int i = 1; i < brackets.size() - 1; i++) {
            assertThat(brackets.get(i).upperBound()).isGreaterThan(brackets.get(i - 1).upperBound());
        }
    }

    @Test
    void payrollRunStatusTransitionsAreDefinedInOrder() {
        assertThat(PayrollConstants.PayrollRunStatus.values()).containsExactly(
                PayrollConstants.PayrollRunStatus.DRAFT, PayrollConstants.PayrollRunStatus.PENDING_APPROVAL,
                PayrollConstants.PayrollRunStatus.APPROVED);
    }
}
