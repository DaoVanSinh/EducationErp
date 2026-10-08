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
import java.time.LocalTime;
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

    /**
     * Ba field mới của spec mục 4. {@code modules.academic} dựa vào ĐÚNG một lần gọi facade này để
     * có đủ dữ liệu cho cả hai việc: kiểm "giáo viên này có dạy lớp này không" ({@code teacherId}) và
     * đếm số buổi đã học ({@code schedule} + {@code standardSessionCount}).
     */
    @Test
    void getClassInfoExposesTeacherScheduleAndStandardSessionCount() {
        var course = courses.save(new Course("FACADE-C4", "Khoá facade 4", null, 24, null));
        var branch = branches.save(new Branch("FCD-B4", "Chi nhánh facade 4", null));
        var teacher = accounts.save(new Account("facade-teacher4@eduerp.local", "hash", "GV 4", null));
        var cls = new Class(course, "FACADE-K4", branch.getId(), teacher.getId(), 12);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        cls.addSchedule(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0));
        var saved = classes.save(cls);

        var info = coursesManagement.getClassInfo(saved.getId()).orElseThrow();

        assertThat(info.teacherId()).isEqualTo(teacher.getId());
        assertThat(info.standardSessionCount()).isEqualTo(24);
        assertThat(info.schedule()).hasSize(2);
        assertThat(info.schedule().get(0).dayOfWeek()).isEqualTo(CoursesConstants.DayOfWeek.MON);
        assertThat(info.schedule().get(0).startTime()).isEqualTo(LocalTime.of(18, 0));
    }

    /** Review Focus #8 + #12 ở tầng facade: hai trạng thái "chưa cấu hình" phải phân biệt được với
     * "không tìm thấy lớp" - Optional có giá trị, hai field bên trong rỗng/null. */
    @Test
    void getClassInfoReportsAnUnconfiguredSessionCountAndAnEmptySchedule() {
        var course = courses.save(new Course("FACADE-C5", "Khoá facade 5", null, null, null));
        var branch = branches.save(new Branch("FCD-B5", "Chi nhánh facade 5", null));
        var teacher = accounts.save(new Account("facade-teacher5@eduerp.local", "hash", "GV 5", null));
        var cls = classes.save(new Class(course, "FACADE-K5", branch.getId(), teacher.getId(), 12));

        var info = coursesManagement.getClassInfo(cls.getId()).orElseThrow();

        assertThat(info.standardSessionCount()).isNull();
        assertThat(info.schedule()).isEmpty();
    }

    @Test
    void getGradeColumnsReturnsTheColumnsInDisplayOrder() {
        var course = new Course("FACADE-C6", "Khoá facade 6", null, 24, null);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        var saved = courses.save(course);

        var columns = coursesManagement.getGradeColumns(saved.getId());

        assertThat(columns).extracting(com.eduerp.modules.courses.dto.GradeColumnResponse::name)
                .as("@OrderBy(displayOrder ASC) quyết định thứ tự, không phải thứ tự thêm vào")
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    /** Khoá không tồn tại trả danh sách rỗng, không ném - nơi gọi (modules.academic) đã có lỗi riêng
     * cho "không tìm thấy lớp", không cần hai lỗi cho cùng một tình huống. */
    @Test
    void getGradeColumnsIsEmptyForAnUnknownCourse() {
        assertThat(coursesManagement.getGradeColumns(UUID.randomUUID())).isEmpty();
    }

    private Course saveOpenCourse(String code, String name, String description, boolean active) {
        var course = new Course(code, name, description, 24, new BigDecimal("7000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    /** Hợp đồng ra ngoài module: modules.chat chỉ được thấy OpenCourseSummary, không bao giờ thấy
     * entity Course. */
    @Test
    void searchOpenCoursesReturnsTheSummaryTheChatbotNeeds() {
        var saved = saveOpenCourse("FCD-S1", "Tiếng Anh thiếu nhi", "Lớp 1 đến lớp 5", true);

        var found = coursesManagement.searchOpenCourses("tiếng Anh thiếu nhi", 5);

        assertThat(found).hasSize(1);
        var summary = found.get(0);
        assertThat(summary.id()).isEqualTo(saved.getId());
        assertThat(summary.code()).isEqualTo("FCD-S1");
        assertThat(summary.name()).isEqualTo("Tiếng Anh thiếu nhi");
        assertThat(summary.description()).isEqualTo("Lớp 1 đến lớp 5");
        assertThat(summary.standardSessionCount()).isEqualTo(24);
        assertThat(summary.tuitionFee()).isEqualByComparingTo(new BigDecimal("7000000"));
    }

    /** Review Focus #3 ở tầng facade. */
    @Test
    void searchOpenCoursesSkipsAnInactiveCourseThatMatches() {
        saveOpenCourse("FCD-S2", "Vẽ cho bé đã đóng", "Mô tả", false);
        saveOpenCourse("FCD-S3", "Vẽ cho bé đang mở", "Mô tả", true);

        assertThat(coursesManagement.searchOpenCourses("vẽ cho bé", 5))
                .extracting(CoursesManagement.OpenCourseSummary::code)
                .containsExactly("FCD-S3");
    }

    /** Cùng triết lý với getGradeColumns: "không khớp khóa nào" là một danh sách rỗng có nghĩa, không
     * phải một lỗi (spec mục 3.3). */
    @Test
    void searchOpenCoursesIsAnEmptyListWhenNothingMatches() {
        saveOpenCourse("FCD-S4", "Cờ vua nhập môn", "Mô tả", true);

        assertThat(coursesManagement.searchOpenCourses("hoá học hữu cơ", 5)).isEmpty();
    }

    /** Khóa chưa chốt giá/chưa cấu hình số buổi vẫn phải truy xuất được - chatbot sẽ nói "chưa công
     * bố học phí" thay vì không thấy khóa đó (xem ChatPromptRules). */
    @Test
    void searchOpenCoursesReturnsACourseWithoutAFeeOrASessionCount() {
        var course = new Course("FCD-S5", "Thư pháp cơ bản", "Mô tả", null, null);
        courses.saveAndFlush(course);

        var summary = coursesManagement.searchOpenCourses("thư pháp", 5).get(0);

        assertThat(summary.standardSessionCount()).isNull();
        assertThat(summary.tuitionFee()).isNull();
    }
}
