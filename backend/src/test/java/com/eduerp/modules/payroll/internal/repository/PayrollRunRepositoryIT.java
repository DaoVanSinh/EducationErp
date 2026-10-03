package com.eduerp.modules.payroll.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** payslips.account_id có FK thật tới accounts(id) (như employment_contracts ở Task 5) - dùng
 * account thật qua AccountRepository, không UUID ngẫu nhiên, để tránh vi phạm FK lúc flush. */
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
        payslips.save(new Payslip(run, newAccountId("payslip1@eduerp.local"), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), BigDecimal.ZERO, new BigDecimal("1050000"), new BigDecimal("2150000"),
                null, false));

        assertThat(payslips.findAllByPayrollRun_Id(run.getId())).hasSize(1);
    }

    /** Review Focus #4 ở tầng DB: unique (year, month) chặn trùng kỳ, usecase (Task 11) phải bắt lỗi
     * này TRƯỚC khi chạm DB bằng existsByYearAndMonth, không để lộ exception thô này ra HTTP. */
    @Test
    void rejectsADuplicateYearMonthAtDatabaseLevel() {
        runs.saveAndFlush(new PayrollRun(2026, 2));

        assertThatThrownBy(() -> runs.saveAndFlush(new PayrollRun(2026, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByYearAndMonthFindsExistingRun() {
        runs.save(new PayrollRun(2026, 3));

        assertThat(runs.existsByYearAndMonth(2026, 3)).isTrue();
        assertThat(runs.existsByYearAndMonth(2026, 4)).isFalse();
    }
}
