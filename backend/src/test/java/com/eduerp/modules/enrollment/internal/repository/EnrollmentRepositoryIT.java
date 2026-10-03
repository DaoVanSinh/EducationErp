package com.eduerp.modules.enrollment.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mọi cột UUID của enrollments đều có FK thật (student_profiles, classes, courses, branches,
 * accounts) - phải dựng dữ liệu thật qua repository của từng module, không dùng UUID ngẫu nhiên
 * (mirror ghi chú của PayrollRunRepositoryIT). */
@Testcontainers
@DataJpaTest
class EnrollmentRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

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

    @BeforeEach
    void seedReferences() {
        branchId = branches.save(new Branch("ENR-" + shortId(), "Chi nhánh ghi danh", null)).getId();
        actorId = accounts.save(new Account("enr-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null)).getId();
        var course = courses.save(new Course("ENR-C-" + shortId(), "Khoá ghi danh", null, 24,
                new BigDecimal("9000000")));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("enr-gv-" + shortId() + "@eduerp.local", "hash", "GV", null)).getId();
        classId = classes.save(new Class(course, "ENR-K-" + shortId(), branchId, teacherId, 2)).getId();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("enr-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private Enrollment newEnrollment(UUID studentProfileId) {
        return new Enrollment(studentProfileId, classId, courseId, branchId, actorId);
    }

    @Test
    void savesAnEnrollmentAsActive() {
        var saved = enrollments.save(newEnrollment(newStudentProfileId()));

        var found = enrollments.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(found.getEnrolledAt()).isNotNull();
        assertThat(found.getWithdrawnAt()).isNull();
        assertThat(found.getCourseId()).isEqualTo(courseId);
        assertThat(found.getBranchId()).isEqualTo(branchId);
    }

    /**
     * Review Focus #1, tầng DB: partial unique index {@code (student_profile_id, class_id) WHERE
     * status = 'ACTIVE'} là lớp phòng thủ thứ hai. Usecase (Task 6) phải chặn TRƯỚC khi chạm DB; test
     * này chứng minh dù usecase hỏng thì DB vẫn không nhận bản ghi trùng.
     */
    @Test
    void rejectsASecondActiveEnrollmentForTheSameStudentAndClassAtDatabaseLevel() {
        var studentProfileId = newStudentProfileId();
        enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThatThrownBy(() -> enrollments.saveAndFlush(newEnrollment(studentProfileId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Review Focus #1, mặt còn lại: index là PARTIAL nên sau khi rút khỏi lớp, học viên được ghi danh
     * lại cùng lớp đó. Một unique index đầy đủ sẽ chặn sai ở đây.
     */
    @Test
    void allowsReEnrollingAfterWithdrawal() {
        var studentProfileId = newStudentProfileId();
        var first = enrollments.saveAndFlush(newEnrollment(studentProfileId));
        first.withdraw();
        enrollments.saveAndFlush(first);

        var second = enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThat(second.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    void countsOnlyActiveEnrollmentsOfTheClass() {
        enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        var withdrawn = enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        withdrawn.withdraw();
        enrollments.saveAndFlush(withdrawn);

        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.WITHDRAWN))
                .isEqualTo(1);
    }

    @Test
    void existsByStudentProfileIdAndClassIdAndStatusSeesOnlyTheMatchingStatus() {
        var studentProfileId = newStudentProfileId();
        var enrollment = enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThat(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).isTrue();

        enrollment.complete();
        enrollments.saveAndFlush(enrollment);

        assertThat(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).isFalse();
    }

    @Test
    void searchFiltersByEveryCombinationOfStudentAndClass() {
        var studentProfileId = newStudentProfileId();
        enrollments.saveAndFlush(newEnrollment(studentProfileId));
        enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        var pageable = PageRequest.of(0, 20);

        assertThat(enrollments.search(null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, null, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(null, classId, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, classId, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(studentProfileId, UUID.randomUUID(), pageable).getTotalElements()).isZero();
    }
}
