package com.eduerp.modules.courses;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.redis.testcontainers.RedisContainer;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Hai method facade mà modules.enrollment/modules.billing sẽ gọi - hợp đồng ra ngoài module, nên
 * test qua context thật chứ không mock repository. */
@Testcontainers
@SpringBootTest
class CoursesManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    CoursesManagement coursesManagement;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    AccountRepository accounts;

    @Test
    void getClassInfoReturnsTheSnapshotEnrollmentNeeds() {
        var course = courses.save(new Course("FACADE-C1", "Khoá facade 1", null, 24, new BigDecimal("9000000")));
        var branch = branches.save(new Branch("FCD-B1", "Chi nhánh facade 1", null));
        var teacher = accounts.save(new Account("facade-teacher1@eduerp.local", "hash", "GV Facade", null));
        var cls = classes.save(new Class(course, "FACADE-K1", branch.getId(), teacher.getId(), 12));

        var info = coursesManagement.getClassInfo(cls.getId()).orElseThrow();

        assertThat(info.classId()).isEqualTo(cls.getId());
        assertThat(info.courseId()).isEqualTo(course.getId());
        assertThat(info.branchId()).isEqualTo(branch.getId());
        assertThat(info.maxSeats()).isEqualTo(12);
        assertThat(info.active()).isTrue();
    }

    @Test
    void getClassInfoIsEmptyForAnUnknownClass() {
        assertThat(coursesManagement.getClassInfo(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getCourseTuitionReturnsTheConfiguredFee() {
        var course = courses.save(new Course("FACADE-C2", "Khoá facade 2", null, 24, new BigDecimal("12000000")));

        var tuition = coursesManagement.getCourseTuition(course.getId()).orElseThrow();

        assertThat(tuition.courseId()).isEqualTo(course.getId());
        assertThat(tuition.tuitionFee()).isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(tuition.active()).isTrue();
    }

    /** Khoá chưa gắn giá vẫn trả về Optional có giá trị, tuitionFee = null - modules.billing phân
     * biệt "không có khoá" (empty) với "khoá chưa gắn giá" (tuitionFee null) bằng hai lỗi khác nhau. */
    @Test
    void getCourseTuitionReturnsANullFeeForACourseWithoutAPrice() {
        var course = courses.save(new Course("FACADE-C3", "Khoá facade 3", null, null, null));

        assertThat(coursesManagement.getCourseTuition(course.getId()).orElseThrow().tuitionFee()).isNull();
    }

    @Test
    void getCourseTuitionIsEmptyForAnUnknownCourse() {
        assertThat(coursesManagement.getCourseTuition(UUID.randomUUID())).isEmpty();
    }
}
