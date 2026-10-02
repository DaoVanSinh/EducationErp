package com.eduerp.modules.courses.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class CourseRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CourseRepository courses;

    @Test
    void savesAndFindsByCode() {
        courses.save(new Course("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24));

        var found = courses.findByCode("TA-GT");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tiếng Anh giao tiếp");
        assertThat(found.get().isActive()).isTrue();
    }
}
