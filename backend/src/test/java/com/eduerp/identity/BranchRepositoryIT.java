package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class BranchRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    BranchRepository branches;

    @Test
    void savesAndFindsByCode() {
        branches.save(new Branch("HN01", "Chi nhánh Hà Nội", "123 Cau Giay"));

        var found = branches.findByCode("HN01");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Chi nhánh Hà Nội");
        assertThat(found.get().isActive()).isTrue();
    }
}
