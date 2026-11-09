package com.eduerp.modules.teachers.internal.rules;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import com.eduerp.modules.teachers.dto.AllowanceItem;
import com.eduerp.modules.teachers.dto.TeacherPayTermsRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeacherPayRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final List<AllowanceItem> ONE_ALLOWANCE = List.of(new AllowanceItem("Ăn trưa", AMOUNT));

    private static TeacherPayTermsRequest monthly(BigDecimal salary, BigDecimal rate, LocalDate probationEnd) {
        return new TeacherPayTermsRequest(PayType.MONTHLY, salary, rate, START, probationEnd, 2, ONE_ALLOWANCE);
    }

    private static TeacherPayTermsRequest hourly(BigDecimal salary, BigDecimal rate, LocalDate probationEnd,
            int dependents, List<AllowanceItem> allowances) {
        return new TeacherPayTermsRequest(PayType.HOURLY, salary, rate, START, probationEnd, dependents, allowances);
    }

    private static void assertRefused(TeacherPayTermsRequest terms) {
        assertThatThrownBy(() -> TeacherPayRules.requireValid(terms)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void acceptsCompleteTerms() {
        assertThatCode(() -> TeacherPayRules.requireValid(monthly(AMOUNT, null, START.plusMonths(2))))
                .doesNotThrowAnyException();
        assertThatCode(() -> TeacherPayRules.requireValid(hourly(null, AMOUNT, null, 0, List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void monthlyNeedsAPositiveSalaryAndNoHourlyRate() {
        assertRefused(monthly(null, null, null));
        assertRefused(monthly(BigDecimal.ZERO, null, null));
        assertRefused(monthly(AMOUNT, AMOUNT, null));
    }

    @Test
    void probationCannotEndBeforeWorkStarts() {
        assertRefused(monthly(AMOUNT, null, START.minusDays(1)));
    }

    @Test
    void hourlyNeedsARateAndNoMonthlyOnlyFields() {
        assertRefused(hourly(null, null, null, 0, List.of()));
        assertRefused(hourly(AMOUNT, AMOUNT, null, 0, List.of()));
        assertRefused(hourly(null, AMOUNT, START, 0, List.of()));
        assertRefused(hourly(null, AMOUNT, null, 1, List.of()));
        assertRefused(hourly(null, AMOUNT, null, 0, ONE_ALLOWANCE));
    }
}
