package com.eduerp.modules.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class StudentsManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    StudentsManagement studentsManagement;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    AccountRepository accounts;

    @Test
    void getProfileReturnsTheSummaryEnrollmentNeeds() {
        var account = accounts.save(new Account("facade-student1@eduerp.local", "hash", "HV Facade", null));
        var profile = profiles.save(new StudentProfile(account.getId(), null, null, null));

        var summary = studentsManagement.getProfile(profile.getId()).orElseThrow();

        assertThat(summary.studentProfileId()).isEqualTo(profile.getId());
        assertThat(summary.accountId()).isEqualTo(account.getId());
        assertThat(summary.active()).isTrue();
    }

    @Test
    void getProfileReportsAnInactiveProfile() {
        var account = accounts.save(new Account("facade-student2@eduerp.local", "hash", "HV Facade 2", null));
        var profile = new StudentProfile(account.getId(), null, null, null);
        profile.setActive(false);
        var saved = profiles.save(profile);

        assertThat(studentsManagement.getProfile(saved.getId()).orElseThrow().active()).isFalse();
    }

    @Test
    void getProfileIsEmptyForAnUnknownProfile() {
        assertThat(studentsManagement.getProfile(UUID.randomUUID())).isEmpty();
    }
}
