package com.eduerp.modules.payroll.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** payslips.account_id có FK thật tới accounts(id) - dùng account thật qua AccountRepository, không UUID ngẫu
 * nhiên, để tránh vi phạm FK lúc flush. */
@Testcontainers
@DataJpaTest
class PayrollRunRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PayrollRunRepository runs;

    @Autowired
    PayslipRepository payslips;

    @Autowired
    AccountRepository accounts;

    private UUID newAccountId(String email) {
        return accounts.save(new Account(email, "hash", "Test User", null)).getId();
    }

    @Test
    void savesARunWithPayslips() {
        var run = runs.save(new PayrollRun(2026, 1));
        payslips.save(PayslipFixtures.monthly(run, newAccountId("payslip1@eduerp.local"), 10_000_000));

        assertThat(payslips.findAllByPayrollRun_Id(run.getId())).hasSize(1);
    }

    /** Review Focus #4 ở tầng DB: unique (year, month) chặn trùng kỳ, usecase phải bắt lỗi này TRƯỚC khi chạm DB
     * bằng existsByYearAndMonth, không để lộ exception thô này ra HTTP. */
    @Test
    void rejectsADuplicateYearMonthAtDatabaseLevel() {
        runs.saveAndFlush(new PayrollRun(2026, 2));

        assertThatThrownBy(() -> runs.saveAndFlush(new PayrollRun(2026, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTeacherHasAtMostOnePayslipPerRun() {
        var run = runs.save(new PayrollRun(2026, 4));
        var accountId = newAccountId("payslip-dup@eduerp.local");
        payslips.saveAndFlush(PayslipFixtures.monthly(run, accountId, 10_000_000));

        assertThatThrownBy(() -> payslips.saveAndFlush(PayslipFixtures.monthly(run, accountId, 10_000_000)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByYearAndMonthFindsExistingRun() {
        runs.save(new PayrollRun(2026, 3));

        assertThat(runs.existsByYearAndMonth(2026, 3)).isTrue();
        assertThat(runs.existsByYearAndMonth(2026, 4)).isFalse();
    }

    /** Review finding Important #6: ListPayrollRuns đếm/cộng bằng một query theo lô cho cả trang thay vì một query
     * riêng cho từng dòng (N+1). Query này xuất phát FROM Payslip nên một kỳ lương chưa có phiếu nào tự nhiên không
     * xuất hiện trong kết quả. */
    @Test
    void aggregateByPayrollRunIdsCountsAndSumsPerRun() {
        var runWithPayslips = runs.save(new PayrollRun(2026, 5));
        var emptyRun = runs.save(new PayrollRun(2026, 6));
        payslips.save(PayslipFixtures.monthly(runWithPayslips, newAccountId("agg1@eduerp.local"), 10_000_000));
        var hourly = PayslipFixtures.hourly(runWithPayslips, newAccountId("agg2@eduerp.local"), 3_000_000, 20);
        hourly.changeAdjustment(new BigDecimal("500000"), "Thưởng");
        payslips.save(hourly);
        payslips.flush();

        var aggregates = payslips.aggregateByPayrollRunIds(List.of(runWithPayslips.getId(), emptyRun.getId()));

        var withPayslipsRow = aggregates.stream()
                .filter(row -> row.getPayrollRunId().equals(runWithPayslips.getId())).findFirst().orElseThrow();
        assertThat(withPayslipsRow.getPayslipCount()).isEqualTo(2);
        assertThat(withPayslipsRow.getTotalGrossPay()).isEqualByComparingTo("13000000");
        assertThat(withPayslipsRow.getTotalNetPay()).as("8.950.000 + 3.500.000").isEqualByComparingTo("12450000");
        assertThat(withPayslipsRow.getTotalEmployerInsurance()).isEqualByComparingTo("2150000");
        assertThat(aggregates.stream().anyMatch(row -> row.getPayrollRunId().equals(emptyRun.getId()))).isFalse();
    }

    /** Giáo viên chỉ thấy kỳ đã duyệt, kỳ mới nhất trước. */
    @Test
    void myPayslipsOnlyComeFromApprovedRunsNewestFirst() {
        var accountId = newAccountId("mine@eduerp.local");
        var older = new PayrollRun(2025, 11);
        older.approve();
        var newer = new PayrollRun(2025, 12);
        newer.approve();
        payslips.save(PayslipFixtures.monthly(runs.save(older), accountId, 1_000_000));
        payslips.save(PayslipFixtures.monthly(runs.save(newer), accountId, 2_000_000));
        payslips.save(PayslipFixtures.monthly(runs.save(new PayrollRun(2025, 10)), accountId, 3_000_000));
        payslips.flush();

        var page = payslips.findAllByAccountIdAndPayrollRun_StatusOrderByPayrollRun_YearDescPayrollRun_MonthDesc(
                accountId, PayrollConstants.PayrollRunStatus.APPROVED, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(payslip -> payslip.getPayrollRun().getMonth()).containsExactly(12, 11);
    }
}
