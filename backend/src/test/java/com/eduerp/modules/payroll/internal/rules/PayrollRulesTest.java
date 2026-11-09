package com.eduerp.modules.payroll.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.teachers.TeachersConstants.PayType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Đối chiếu từng nhánh của công thức trong spec 2026-11-05-luong-giao-vien-design.md, mục 5. */
class PayrollRulesTest {

    /** Tháng 30 ngày. */
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount);
    }

    private static PayInput monthly(long salary, LocalDate start, LocalDate probationEnd, LocalDate lastWork,
            int dependents, long allowances, long adjustment) {
        return new PayInput(PayType.MONTHLY, vnd(salary), null, start, probationEnd, lastWork, dependents,
                vnd(allowances), 0, 0, vnd(adjustment));
    }

    private static PayInput hourly(long rate, int minutes, long adjustment) {
        return new PayInput(PayType.HOURLY, null, vnd(rate), LONG_AGO, null, null, 0, BigDecimal.ZERO, 10, minutes,
                vnd(adjustment));
    }

    private static PayslipFigures calculate(PayInput input) {
        return PayrollRules.calculate(input, NOVEMBER).orElseThrow();
    }

    private static BigDecimal netOf(PayslipFigures figures, long adjustment) {
        return PayrollRules.netPay(figures.grossPay(), figures.allowancesTotal(), vnd(adjustment),
                figures.socialInsuranceEmployee(), figures.incomeTaxWithheld());
    }

    @Test
    void workedExampleOfAMonthlyTeacherFromTheSpec() {
        var figures = calculate(monthly(15_000_000, LONG_AGO, null, null, 1, 1_130_000, 0));

        assertThat(figures.grossPay()).isEqualByComparingTo("15000000");
        assertThat(figures.allowancesTotal()).isEqualByComparingTo("1130000");
        assertThat(figures.socialInsuranceEmployee()).isEqualByComparingTo("1575000");
        assertThat(figures.socialInsuranceEmployer()).isEqualByComparingTo("3225000");
        assertThat(figures.incomeTaxWithheld()).isEqualByComparingTo("0");
        assertThat(netOf(figures, 0)).isEqualByComparingTo("14555000");
    }

    @Test
    void workedExampleOfAnHourlyTeacherFromTheSpec() {
        var figures = calculate(hourly(250_000, 36 * 60, 0));

        assertThat(figures.hoursWorked()).isEqualByComparingTo("36");
        assertThat(figures.grossPay()).isEqualByComparingTo("9000000");
        assertThat(figures.socialInsuranceEmployee()).isEqualByComparingTo("0");
        assertThat(figures.socialInsuranceEmployer()).isEqualByComparingTo("0");
        assertThat(figures.incomeTaxWithheld()).isEqualByComparingTo("900000");
        assertThat(netOf(figures, 0)).isEqualByComparingTo("8100000");
    }

    @Test
    void aTeacherJoiningMidMonthIsPaidOnlyTheDaysWorked() {
        var figures = calculate(monthly(30_000_000, LocalDate.of(2026, 11, 16), null, null, 0, 3_000_000, 0));

        assertThat(figures.paidDays()).isEqualTo(15);
        assertThat(figures.grossPay()).isEqualByComparingTo("15000000");
        assertThat(figures.allowancesTotal()).as("phụ cấp theo ngày làm").isEqualByComparingTo("1500000");
    }

    @Test
    void aTeacherLeavingMidMonthIsPaidUpToTheLastWorkDate() {
        var figures = calculate(monthly(30_000_000, LONG_AGO, null, LocalDate.of(2026, 11, 10), 0, 0, 0));

        assertThat(figures.paidDays()).isEqualTo(10);
        assertThat(figures.grossPay()).isEqualByComparingTo("10000000");
    }

    /** Lỗi của công thức cũ: chốt thử việc tại ngày 01 nên người vào giữa tháng được tính 100%. */
    @Test
    void aTeacherStartingProbationMidMonthIsPaidEightyFivePercentForThoseDays() {
        var figures = calculate(monthly(30_000_000, LocalDate.of(2026, 11, 16), LocalDate.of(2027, 1, 15), null, 0,
                0, 0));

        assertThat(figures.probationDays()).isEqualTo(15);
        assertThat(figures.grossPay()).isEqualByComparingTo("12750000");
    }

    @Test
    void probationEndingMidMonthSplitsTheMonth() {
        var figures = calculate(monthly(30_000_000, LONG_AGO, LocalDate.of(2026, 11, 10), null, 0, 0, 0));

        assertThat(figures.paidDays()).isEqualTo(30);
        assertThat(figures.probationDays()).isEqualTo(10);
        assertThat(figures.grossPay()).as("20 ngày chính thức + 10 x 85%").isEqualByComparingTo("28500000");
    }

    @Test
    void probationThatEndedBeforeThePeriodDoesNotApply() {
        var figures = calculate(monthly(30_000_000, LONG_AGO, LocalDate.of(2026, 10, 31), null, 0, 0, 0));

        assertThat(figures.probationDays()).isZero();
        assertThat(figures.grossPay()).isEqualByComparingTo("30000000");
    }

    @Test
    void aMonthlyTeacherWithNoDayInThePeriodGetsNoPayslip() {
        assertThat(PayrollRules.calculate(monthly(15_000_000, LocalDate.of(2026, 12, 1), null, null, 0, 0, 0),
                NOVEMBER)).isEmpty();
        assertThat(PayrollRules.calculate(monthly(15_000_000, LONG_AGO, null, LocalDate.of(2026, 10, 31), 0, 0, 0),
                NOVEMBER)).isEmpty();
    }

    @Test
    void anHourlyTeacherWithNoSessionGetsNoPayslip() {
        assertThat(PayrollRules.calculate(hourly(250_000, 0, 0), NOVEMBER)).isEmpty();
    }

    @Test
    void hoursAreRoundedSoThePayslipCanBeRecomputedByHand() {
        var figures = calculate(hourly(100_000, 100, 0));

        assertThat(figures.hoursWorked()).isEqualByComparingTo("1.67");
        assertThat(figures.grossPay()).isEqualByComparingTo("167000");
    }

    @Test
    void socialInsuranceIsCappedAtTwentyTimesTheReferenceLevel() {
        var figures = calculate(monthly(60_000_000, LONG_AGO, null, null, 0, 0, 0));

        assertThat(figures.socialInsuranceEmployee()).as("10,5% x 50.600.000").isEqualByComparingTo("5313000");
        assertThat(figures.socialInsuranceEmployer()).as("21,5% x 50.600.000").isEqualByComparingTo("10879000");
    }

    @Test
    void progressiveTaxWalksEveryBracket() {
        assertThat(PayrollRules.progressiveTax(vnd(-1))).isEqualByComparingTo("0");
        assertThat(PayrollRules.progressiveTax(vnd(0))).isEqualByComparingTo("0");
        assertThat(PayrollRules.progressiveTax(vnd(10_000_000))).isEqualByComparingTo("500000");
        assertThat(PayrollRules.progressiveTax(vnd(30_000_000))).isEqualByComparingTo("2500000");
        assertThat(PayrollRules.progressiveTax(vnd(40_000_000))).isEqualByComparingTo("4500000");
        assertThat(PayrollRules.progressiveTax(vnd(60_000_000))).isEqualByComparingTo("8500000");
        assertThat(PayrollRules.progressiveTax(vnd(100_000_000))).isEqualByComparingTo("20500000");
        assertThat(PayrollRules.progressiveTax(vnd(120_000_000))).isEqualByComparingTo("27500000");
    }

    @Test
    void dependentsReduceTheTaxOfAMonthlyTeacher() {
        var noDependent = calculate(monthly(40_000_000, LONG_AGO, null, null, 0, 0, 0));
        var twoDependents = calculate(monthly(40_000_000, LONG_AGO, null, null, 2, 0, 0));

        // 40.000.000 - 4.200.000 BHXH - 15.500.000 = 20.300.000 tính thuế: 500.000 + 1.030.000.
        assertThat(noDependent.incomeTaxWithheld()).isEqualByComparingTo("1530000");
        // Bớt thêm 12.400.000 còn 7.900.000 tính thuế, nằm trọn bậc 5%.
        assertThat(twoDependents.incomeTaxWithheld()).isEqualByComparingTo("395000");
    }

    @Test
    void hourlyWithholdingAppliesFromTheThresholdOnTheWholeAmount() {
        assertThat(calculate(hourly(100_000, 49 * 60, 0)).incomeTaxWithheld()).as("4.900.000").isEqualByComparingTo("0");
        assertThat(calculate(hourly(100_000, 50 * 60, 0)).incomeTaxWithheld()).as("5.000.000")
                .isEqualByComparingTo("500000");
    }

    @Test
    void anAdjustmentCountsTowardsIncomeAndCanCrossTheThreshold() {
        var figures = calculate(hourly(100_000, 49 * 60, 200_000));

        assertThat(figures.incomeTaxWithheld()).as("10% x 5.100.000").isEqualByComparingTo("510000");
        assertThat(netOf(figures, 200_000)).isEqualByComparingTo("4590000");
    }
}
