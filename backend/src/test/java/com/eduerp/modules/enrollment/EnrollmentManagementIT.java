package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Hợp đồng ra ngoài module mà {@code modules.academic} sẽ gọi - test qua context thật, mirror
 * {@code CoursesManagementIT}. */
@Testcontainers
@SpringBootTest
class EnrollmentManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    EnrollmentManagement enrollmentManagement;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    AccountRepository accounts;

    @Autowired
    StudentProfileRepository profiles;

    private UUID branchId;
    private UUID actorId;
    private UUID courseId;
    private UUID classId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedReferences() {
        branchId = branches.save(new Branch("ROS-" + shortId(), "Chi nhánh sổ điểm", null)).getId();
        actorId = accounts.save(new Account("ros-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        var course = courses.save(new Course("ROS-C-" + shortId(), "Khoá sổ điểm", null, 24, null));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("ros-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        classId = classes.save(new Class(course, "ROS-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("ros-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private Enrollment saveEnrollment(UUID studentProfileId) {
        return enrollments.saveAndFlush(
                new Enrollment(studentProfileId, classId, courseId, branchId, actorId));
    }

    @Test
    void listsEveryEnrolmentOfTheClassWithTheDatesProgressNeeds() {
        var studentProfileId = newStudentProfileId();
        var enrollment = saveEnrollment(studentProfileId);

        var roster = enrollmentManagement.listActiveEnrollmentsByClass(classId);

        assertThat(roster).hasSize(1);
        var entry = roster.get(0);
        assertThat(entry.enrollmentId()).isEqualTo(enrollment.getId());
        assertThat(entry.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(entry.courseId()).isEqualTo(courseId);
        assertThat(entry.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(entry.enrolledAt()).isNotNull();
        assertThat(entry.withdrawnAt()).isNull();
    }

    /**
     * Review Focus #2: học viên đã rút PHẢI còn trong danh sách, kèm {@code withdrawnAt}, vì tiến
     * trình của họ bị đóng băng tại mốc đó chứ không biến mất khỏi báo cáo.
     */
    @Test
    void keepsWithdrawnEnrolmentsSoProgressCanFreezeThemAtTheirWithdrawnAt() {
        var withdrawn = saveEnrollment(newStudentProfileId());
        withdrawn.withdraw();
        enrollments.saveAndFlush(withdrawn);

        var roster = enrollmentManagement.listActiveEnrollmentsByClass(classId);

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.WITHDRAWN);
        assertThat(roster.get(0).withdrawnAt()).isNotNull();
    }

    @Test
    void doesNotLeakEnrolmentsOfAnotherClass() {
        saveEnrollment(newStudentProfileId());

        assertThat(enrollmentManagement.listActiveEnrollmentsByClass(UUID.randomUUID())).isEmpty();
    }
}
