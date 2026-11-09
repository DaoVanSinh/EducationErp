package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.payroll.PayrollConstants.IncomeTax;
import com.eduerp.modules.payroll.PayrollConstants.StatutoryRates;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Công thức lương - pure, 100% unit-test được bằng new, không I/O. Bản đối chiếu cho người đọc:
 * {@code docs/superpowers/specs/2026-11-05-luong-giao-vien-design.md}, mục 5.
 */
public final class PayrollRules {

    /** Tiền Việt không có phần lẻ: mọi khoản làm tròn tới đồng để con số trên phiếu cộng trừ khớp nhau. */
    private static final int MONEY_SCALE = 0;
    private static final int HOURS_SCALE = 2;
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private PayrollRules() {
    }

    /** Số ngày làm việc trong kỳ và số ngày trong đó còn thử việc. */
    public record PaidDays(int total, int probation) {
    }

    /**
     * Phiếu lương của một giáo viên trong kỳ, hoặc rỗng khi kỳ này không có gì để trả: trả theo tháng mà không có
     * ngày làm việc nào, hoặc trả theo giờ mà không có buổi dạy nào.
     */
    public static Optional<PayslipFigures> calculate(PayInput input, YearMonth period) {
        return input.payType() == PayType.MONTHLY ? calculateMonthly(input, period) : calculateHourly(input);
    }

    private static Optional<PayslipFigures> calculateMonthly(PayInput input, YearMonth period) {
        var paidDays = paidDays(input.workStartDate(), input.lastWorkDate(), input.probationEndDate(), period);
        if (paidDays.total() == 0) {
            return Optional.empty();
        }
        var grossPay = monthlyBasePay(input.monthlySalary(), paidDays, period.lengthOfMonth());
        var allowances = proratedAllowances(input.allowancesTotal(), paidDays.total(), period.lengthOfMonth());
        var employeeShare = socialInsurance(grossPay, StatutoryRates.EMPLOYEE_SHARE);
        var tax = incomeTax(PayType.MONTHLY, grossPay, allowances, input.adjustment(), employeeShare,
                input.dependentCount());
        return Optional.of(new PayslipFigures(PayType.MONTHLY, input.monthlySalary(), input.sessionCount(),
                hours(input.totalMinutes()), paidDays.total(), paidDays.probation(), input.dependentCount(), grossPay,
                allowances, employeeShare, socialInsurance(grossPay, StatutoryRates.EMPLOYER_SHARE), tax));
    }

    private static Optional<PayslipFigures> calculateHourly(PayInput input) {
        if (input.totalMinutes() == 0) {
            return Optional.empty();
        }
        var hoursWorked = hours(input.totalMinutes());
        var grossPay = money(input.hourlyRate().multiply(hoursWorked));
        var tax = incomeTax(PayType.HOURLY, grossPay, BigDecimal.ZERO, input.adjustment(), BigDecimal.ZERO, 0);
        return Optional.of(new PayslipFigures(PayType.HOURLY, input.hourlyRate(), input.sessionCount(), hoursWorked,
                0, 0, 0, grossPay, money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO), tax));
    }

    /** Từ ngày bắt đầu (hoặc đầu tháng) tới ngày nghỉ (hoặc cuối tháng), tính cả hai đầu. */
    public static PaidDays paidDays(LocalDate workStartDate, LocalDate lastWorkDate, LocalDate probationEndDate,
            YearMonth period) {
        var from = later(workStartDate, period.atDay(1));
        var to = lastWorkDate == null ? period.atEndOfMonth() : earlier(lastWorkDate, period.atEndOfMonth());
        if (from.isAfter(to)) {
            return new PaidDays(0, 0);
        }
        var probationDays = probationEndDate == null || probationEndDate.isBefore(from)
                ? 0 : daysInclusive(from, earlier(probationEndDate, to));
        return new PaidDays(daysInclusive(from, to), probationDays);
    }

    /** Lương tháng / D x ((N - Nt) + 85% x Nt). */
    public static BigDecimal monthlyBasePay(BigDecimal monthlySalary, PaidDays paidDays, int daysInPeriod) {
        var officialDays = BigDecimal.valueOf((long) paidDays.total() - paidDays.probation());
        var probationDays = BigDecimal.valueOf(paidDays.probation()).multiply(StatutoryRates.PROBATION_RATE);
        return monthlySalary.multiply(officialDays.add(probationDays))
                .divide(BigDecimal.valueOf(daysInPeriod), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal proratedAllowances(BigDecimal allowancesTotal, int paidDays, int daysInPeriod) {
        return allowancesTotal.multiply(BigDecimal.valueOf(paidDays))
                .divide(BigDecimal.valueOf(daysInPeriod), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Giờ làm tròn 2 chữ số, lưu trên phiếu và nhân với đơn giá: người đọc phiếu tính lại ra đúng số đó. */
    public static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, HOURS_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal socialInsurance(BigDecimal grossPay, BigDecimal rate) {
        return money(grossPay.min(StatutoryRates.SOCIAL_INSURANCE_SALARY_CAP).multiply(rate));
    }

    /**
     * Trả theo tháng: biểu lũy tiến trên phần sau giảm trừ. Trả theo giờ: 10% toàn bộ khoản trả nếu đạt ngưỡng.
     * Thưởng/điều chỉnh tính vào thu nhập, nên đổi điều chỉnh thì thuế đổi theo.
     */
    public static BigDecimal incomeTax(PayType payType, BigDecimal grossPay, BigDecimal allowances,
            BigDecimal adjustment, BigDecimal socialInsuranceEmployee, int dependentCount) {
        var income = grossPay.add(allowances).add(adjustment);
        if (payType == PayType.HOURLY) {
            return income.compareTo(IncomeTax.HOURLY_WITHHOLDING_THRESHOLD) >= 0
                    ? money(income.multiply(IncomeTax.HOURLY_WITHHOLDING_RATE)) : money(BigDecimal.ZERO);
        }
        var deductions = IncomeTax.PERSONAL_DEDUCTION
                .add(IncomeTax.DEPENDENT_DEDUCTION.multiply(BigDecimal.valueOf(dependentCount)))
                .add(socialInsuranceEmployee);
        return progressiveTax(income.subtract(deductions));
    }

    public static BigDecimal progressiveTax(BigDecimal taxableIncome) {
        var tax = BigDecimal.ZERO;
        var lowerBound = BigDecimal.ZERO;
        for (var bracket : IncomeTax.BRACKETS) {
            if (taxableIncome.compareTo(lowerBound) <= 0) {
                break;
            }
            var top = bracket.upperBound() == null ? taxableIncome : taxableIncome.min(bracket.upperBound());
            tax = tax.add(top.subtract(lowerBound).multiply(bracket.rate()));
            lowerBound = bracket.upperBound();
        }
        return money(tax);
    }

    public static BigDecimal netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal adjustment,
            BigDecimal socialInsuranceEmployee, BigDecimal incomeTaxWithheld) {
        return money(grossPay.add(allowances).add(adjustment).subtract(socialInsuranceEmployee)
                .subtract(incomeTaxWithheld));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static int daysInclusive(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to)) + 1;
    }

    private static LocalDate later(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate earlier(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
