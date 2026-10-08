package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class AccountRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesAccountWithHomeBranchAndFindsByEmail() {
        // home_branch_id có FK mức DB tới branches.id (V1), nhưng slice @DataJpaTest này chỉ nạp
        // AccountRepository — chèn thẳng một dòng branches bằng JDBC thay vì kéo cả
        // organization.internal.repository.BranchRepository vào một test của module identity.
        var branchId = UUID.randomUUID();
        jdbc.update("INSERT INTO branches (id, code, name, active) VALUES (?, ?, ?, true)",
                branchId, "QA-" + branchId.toString().substring(0, 8), "Chi nhánh test");

        accounts.save(new Account("teacher@eduerp.local", "hashed", "Nguyễn Văn A", branchId));
        var found = accounts.findByEmail("teacher@eduerp.local");

        assertThat(found).isPresent();
        assertThat(found.get().getHomeBranchId()).isEqualTo(branchId);
        assertThat(found.get().getStatus()).isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
    }

    @Test
    void storesEmailNormalizedSoCaseDoesNotCreateASecondAccountForTheSamePerson() {
        var saved = accounts.save(new Account("  Teacher.Case@EduERP.Local ", "hashed", "Nguyễn Văn B", null));

        assertThat(saved.getEmail()).isEqualTo("teacher.case@eduerp.local");
        assertThat(accounts.findByTypedEmail("TEACHER.CASE@eduerp.LOCAL")).isPresent();
        assertThat(accounts.findByEmail("Teacher.Case@EduERP.Local")).isEmpty();
    }
}
