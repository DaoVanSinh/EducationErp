package com.eduerp.modules.billing.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
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
import java.time.LocalDate;
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

@Testcontainers
@DataJpaTest
class ComboRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    ComboDiscountTierRepository tiers;

    @Autowired
    ComboRepository combos;

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
    private Course course;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedReferences() {
        branchId = branches.save(new Branch("CMB-" + shortId(), "Chi nhánh combo", null)).getId();
        actorId = accounts.save(new Account("cmb-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        course = courses.save(new Course("CMB-C-" + shortId(), "Khoá combo", null, 24,
                new BigDecimal("12000000")));
        courseId = course.getId();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("cmb-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    /**
     * Ruling (Task 2): V17's {@code uq_enrollments_active_student_class} cấm một học viên có hai
     * ghi danh ACTIVE trong CÙNG một Class - brief dùng chung một {@code classId} cho mọi lần gọi,
     * nên hai enrollment cho cùng một học viên trong một test sẽ đụng ràng buộc đó. Mỗi lần gọi tạo
     * một Class mới (cùng Course) để mỗi enrollment là một (student, class) riêng - đúng hình dạng
     * thật của combo (nhiều lớp khác nhau của cùng một học viên).
     */
    private UUID newEnrollmentId(UUID studentProfileId) {
        var teacherId = accounts.save(new Account("cmb-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var classId = classes.save(new Class(course, "CMB-K-" + shortId(), branchId, teacherId, 20)).getId();
        return enrollments.save(new Enrollment(studentProfileId, classId, courseId, branchId, actorId)).getId();
    }

    private Combo newCombo(UUID studentProfileId) {
        return new Combo(studentProfileId, branchId, new BigDecimal("21000000"), new BigDecimal("15"),
                LocalDate.of(2027, 1, 31), actorId);
    }

    @Test
    void savesATierAsActiveWithPercentAtScaleTwo() {
        var saved = tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        var found = tiers.findById(saved.getId()).orElseThrow();
        assertThat(found.getMinCourseCount()).isEqualTo(2);
        assertThat(found.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(found.isActive()).isTrue();
    }

    /** min_course_count UNIQUE: hai bậc cùng mốc sẽ khiến việc chọn bậc phụ thuộc thứ tự dòng. */
    @Test
    void rejectsASecondTierWithTheSameMinCourseCountAtDatabaseLevel() {
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));

        assertThatThrownBy(() -> tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("20"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Spec mục 4: bậc áp dụng là minCourseCount LỚN NHẤT còn active mà không vượt số khoá. */
    @Test
    void picksTheHighestActiveTierThatDoesNotExceedTheCourseCount() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(4)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(1))
                .isEmpty();
    }

    /** Tắt active là cách duy nhất để "xoá" một bậc - bậc tắt không được chọn nữa. */
    @Test
    void skipsAnInactiveTierAndFallsBackToTheNextOneDown() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        var retired = tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        retired.update(new BigDecimal("15"), false);
        tiers.saveAndFlush(retired);

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(3)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void listsTiersInAscendingMinCourseCountOrder() {
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        assertThat(tiers.findAllByOrderByMinCourseCountAsc())
                .extracting(ComboDiscountTier::getMinCourseCount).containsExactly(2, 5);
    }

    @Test
    void savesAComboWithItsEnrollmentsInOneCascade() {
        var studentProfileId = newStudentProfileId();
        var combo = newCombo(studentProfileId);
        combo.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("12000000"));
        combo.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        var saved = combos.saveAndFlush(combo);

        var found = combos.findById(saved.getId()).orElseThrow();
        assertThat(found.getEnrollments()).hasSize(2);
        assertThat(found.getTotalOriginalAmount()).isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(found.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(found.getTotalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getEnrollments()).extracting(ComboEnrollment::getOriginalTuitionFee)
                .allSatisfy(fee -> assertThat(fee.scale()).isZero());
    }

    /**
     * Review Focus #3, tầng DB: hai request đồng thời cùng chọn một enrollment vào hai combo khác
     * nhau. Cả hai đọc trước khi ai kịp ghi, nên UNIQUE trên combo_enrollments.enrollment_id là thứ
     * duy nhất chặn được một trong hai (usecase dịch lỗi này ở Task 7).
     */
    @Test
    void rejectsTheSameEnrollmentInTwoLiveCombosAtDatabaseLevel() {
        var studentProfileId = newStudentProfileId();
        var sharedEnrollmentId = newEnrollmentId(studentProfileId);
        var first = newCombo(studentProfileId);
        first.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        first.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));
        combos.saveAndFlush(first);

        var second = newCombo(studentProfileId);
        second.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        second.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        assertThatThrownBy(() -> combos.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Mặt còn lại: UNIQUE đầy đủ chỉ đúng vì huỷ combo XOÁ CỨNG dòng con - sau khi combo cũ bị xoá,
     * chính enrollment đó phải gộp lại được vào một combo mới. */
    @Test
    void allowsReusingAnEnrollmentAfterItsComboIsDeleted() {
        var studentProfileId = newStudentProfileId();
        var sharedEnrollmentId = newEnrollmentId(studentProfileId);
        var first = newCombo(studentProfileId);
        first.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        first.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));
        var saved = combos.saveAndFlush(first);
        combos.delete(saved);
        combos.flush();

        var second = newCombo(studentProfileId);
        second.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        second.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        assertThat(combos.saveAndFlush(second).getEnrollments()).hasSize(2);
    }

    @Test
    void searchFiltersByOptionalStudentProfileId() {
        var mine = newStudentProfileId();
        var someoneElse = newStudentProfileId();
        var first = newCombo(mine);
        first.addEnrollment(newEnrollmentId(mine), courseId, new BigDecimal("12000000"));
        combos.saveAndFlush(first);
        var second = newCombo(someoneElse);
        second.addEnrollment(newEnrollmentId(someoneElse), courseId, new BigDecimal("12000000"));
        combos.saveAndFlush(second);
        var pageable = PageRequest.of(0, 20);

        assertThat(combos.search(null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(combos.search(mine, pageable).getTotalElements()).isEqualTo(1);
        assertThat(combos.search(UUID.randomUUID(), pageable).getTotalElements()).isZero();
    }
}
