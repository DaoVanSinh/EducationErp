package com.eduerp.modules.academic.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.academic.internal.model.GradeEntry;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mọi cột UUID của grade_entries đều có FK thật (enrollments, course_grade_columns, accounts) -
 * phải dựng dữ liệu thật qua repository của từng module, không dùng UUID ngẫu nhiên (mirror
 * {@code EnrollmentRepositoryIT}). */
@Testcontainers
@DataJpaTest
class GradeEntryRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    GradeEntryRepository gradeEntries;

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

    private UUID teacherId;
    private UUID midtermColumnId;
    private UUID finalColumnId;
    private UUID enrollmentId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedReferences() {
        var branchId = branches.save(new Branch("GE-" + shortId(), "Chi nhánh điểm", null)).getId();
        teacherId = accounts.save(new Account("ge-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var course = new Course("GE-C-" + shortId(), "Khoá có điểm", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = classes.save(new Class(savedCourse, "GE-K-" + shortId(), branchId, teacherId, 20));
        var studentAccount = accounts.save(
                new Account("ge-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        var studentProfileId = profiles.save(
                new StudentProfile(studentAccount.getId(), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, cls.getId(),
                savedCourse.getId(), branchId, teacherId)).getId();
    }

    @Test
    void savesAGradeEntryWithItsAuditTrail() {
        var saved = gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"), teacherId));

        var found = gradeEntries.findById(saved.getId()).orElseThrow();
        assertThat(found.getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(found.getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(found.getCourseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(found.getEnteredByAccountId()).isEqualTo(teacherId);
        assertThat(found.getEnteredAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    /** Spec mục 6: UNIQUE (enrollment_id, course_grade_column_id) - một điểm cho một cột của một
     * lượt ghi danh. Nhập lại là SỬA, không phải thêm dòng thứ hai. */
    @Test
    void rejectsASecondEntryForTheSameEnrolmentAndColumnAtDatabaseLevel() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        assertThatThrownBy(() -> gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("9.00"), teacherId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTwoEntriesForTwoDifferentColumnsOfTheSameEnrolment() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, finalColumnId, new BigDecimal("9.00"),
                teacherId));

        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId))).hasSize(2);
    }

    @Test
    void findsAnExistingEntryByEnrolmentAndColumnSoRecordingCanUpsert() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        var found = gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, finalColumnId))
                .isEmpty();
    }

    /** {@code changeScore} phải đẩy {@code updatedAt} về sau {@code enteredAt} - nhật ký "ai sửa
     * điểm lúc nào" là lý do duy nhất hai cột này tồn tại. */
    @Test
    void changeScoreUpdatesTheScoreAndTheUpdatedAtMark() {
        var saved = gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"), teacherId));
        var enteredAt = saved.getEnteredAt();
        var otherTeacherId = accounts.save(
                new Account("ge-gv2-" + shortId() + "@eduerp.local", "hash", "GV2", null)).getId();

        saved.changeScore(new BigDecimal("9.25"), otherTeacherId);
        gradeEntries.saveAndFlush(saved);

        var reloaded = gradeEntries.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getScore()).isEqualByComparingTo(new BigDecimal("9.25"));
        assertThat(reloaded.getEnteredByAccountId()).isEqualTo(otherTeacherId);
        assertThat(reloaded.getEnteredAt()).isEqualTo(enteredAt);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(enteredAt);
    }

    /** Nạp theo lô cho cả lớp: bảng điểm 30 học viên không được bắn 30 câu SELECT (rule #3). */
    @Test
    void loadsEntriesForManyEnrolmentsInOneCall() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId, UUID.randomUUID())))
                .hasSize(1);
        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of())).isEmpty();
    }
}
