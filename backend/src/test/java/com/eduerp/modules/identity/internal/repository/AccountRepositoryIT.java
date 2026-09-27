package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.model.Branch;
import com.eduerp.modules.identity.internal.model.Role;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
    RoleRepository roles;

    @Autowired
    BranchRepository branches;

    @Test
    void savesAccountWithHomeBranchAndFindsByEmail() {
        var role = roles.save(new Role("QA_JPA_TEST_ROLE", "Giáo viên", true));
        var branch = branches.save(new Branch("HN01", "Chi nhánh Hà Nội", null));

        accounts.save(new Account("teacher@eduerp.local", "hashed", "Nguyễn Văn A", role, branch));
        var found = accounts.findByEmail("teacher@eduerp.local");

        assertThat(found).isPresent();
        assertThat(found.get().getHomeBranch()).isNotNull();
        assertThat(found.get().getStatus()).isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
    }

    @Test
    void storesEmailNormalizedSoCaseDoesNotCreateASecondAccountForTheSamePerson() {
        var role = roles.save(new Role("QA_EMAIL_CASE_ROLE", "Giáo viên", true));

        var saved = accounts.save(new Account("  Teacher.Case@EduERP.Local ", "hashed", "Nguyễn Văn B", role, null));

        assertThat(saved.getEmail()).isEqualTo("teacher.case@eduerp.local");
        assertThat(accounts.findByTypedEmail("TEACHER.CASE@eduerp.LOCAL")).isPresent();
        assertThat(accounts.findByEmail("Teacher.Case@EduERP.Local")).isEmpty();
    }
}
