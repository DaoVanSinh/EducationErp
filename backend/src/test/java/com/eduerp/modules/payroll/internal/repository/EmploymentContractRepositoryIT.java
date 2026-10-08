package com.eduerp.modules.payroll.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * employment_contracts.account_id có FK thật tới accounts(id) (mirror V12 của Phase 2.5) - một
 * UUID ngẫu nhiên không tồn tại account thật sẽ vi phạm FK. Hai trong ba test của bản gốc plan
 * "qua" được vì findById() ngay sau save() trong cùng transaction trả về từ cache cấp 1 của JPA mà
 * không flush thật xuống DB - không chứng minh được gì. existsByAccountIdAndStatus là derived
 * query nên buộc auto-flush, lộ ra FK violation thật. Sửa: tạo Account thật qua AccountRepository
 * trước, dùng id thật đó cho mọi contract.
 */
@Testcontainers
@DataJpaTest
class EmploymentContractRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    EmploymentContractRepository contracts;

    @Autowired
    AccountRepository accounts;

    @Autowired
    TestEntityManager entityManager;

    private UUID newAccountId(String email) {
        return accounts.save(new Account(email, "hash", "Test User", null)).getId();
    }

    @Test
    void savesAnOfficialContractWithAllowances() {
        var accountId = newAccountId("official@eduerp.local");
        var contract = new EmploymentContract(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 1, 1), List.of(new ContractAllowance("Xăng xe", new BigDecimal("500000"))));

        var saved = contracts.save(contract);
        entityManager.flush();
        entityManager.clear();
        var found = contracts.findById(saved.getId()).orElseThrow();

        assertThat(found.getAccountId()).isEqualTo(accountId);
        assertThat(found.getStatus()).isEqualTo(PayrollConstants.ContractStatus.ACTIVE);
        assertThat(found.getAllowances()).extracting("name").containsExactly("Xăng xe");
    }

    @Test
    void existsByAccountIdAndStatusFindsOnlyActiveContracts() {
        var accountId = newAccountId("collaborator@eduerp.local");
        contracts.save(new EmploymentContract(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of()));

        assertThat(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)).isTrue();
        assertThat(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.TERMINATED))
                .isFalse();
    }

    @Test
    void terminateSetsStatusAndEndDate() {
        var contract = contracts.save(new EmploymentContract(newAccountId("terminate@eduerp.local"),
                PayrollConstants.ContractType.OFFICIAL, new BigDecimal("8000000"), null, null, null,
                LocalDate.of(2025, 6, 1), List.of()));

        contract.terminate();
        contracts.save(contract);
        entityManager.flush();
        entityManager.clear();
        var found = contracts.findById(contract.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(PayrollConstants.ContractStatus.TERMINATED);
        assertThat(found.getEndDate()).isNotNull();
    }
}
