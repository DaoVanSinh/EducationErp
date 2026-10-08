package com.eduerp.modules.courses.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mirror {@code ClassSchedule}: cột điểm là entity con cascade ALL + orphanRemoval của Course, sửa
 * = thay TOÀN BỘ danh sách (spec mục 4). */
@Testcontainers
@DataJpaTest
class CourseGradeColumnRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CourseRepository courses;

    private Course newCourseWithTwoColumns(String code) {
        var course = new Course(code, "Tiếng Anh giao tiếp", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        return course;
    }

    @Test
    void savesGradeColumnsAlongWithTheirCourse() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-SAVE"));

        var found = courses.findById(saved.getId()).orElseThrow();
        assertThat(found.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
        assertThat(found.getGradeColumns()).extracting(CourseGradeColumn::getWeightPercent)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("40.00"), new BigDecimal("60.00"));
        assertThat(found.getGradeColumns().get(0).getMaxScore())
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    /** {@code getGradeColumns()} trả bản copy bất biến - nơi gọi không được sửa danh sách trực tiếp
     * mà phải đi qua {@code clearGradeColumns}/{@code addGradeColumn}, mirror {@code Class.getSchedule}. */
    @Test
    void exposesAnImmutableCopyOfTheColumnList() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-IMMUTABLE"));

        var columns = courses.findById(saved.getId()).orElseThrow().getGradeColumns();

        assertThat(columns).isUnmodifiable();
    }

    /** orphanRemoval: thay toàn bộ danh sách phải XOÁ HẲN dòng cũ, không để lại cột mồ côi. */
    @Test
    void replacingTheListDeletesTheOldColumns() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-REPLACE"));
        var reloaded = courses.findById(saved.getId()).orElseThrow();

        reloaded.clearGradeColumns();
        reloaded.addGradeColumn("Chuyên cần", new BigDecimal("100.00"), new BigDecimal("10.00"), 0);
        courses.saveAndFlush(reloaded);

        var afterReplace = courses.findById(saved.getId()).orElseThrow();
        assertThat(afterReplace.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Chuyên cần");
    }

    /** Khóa học cũ (mọi khóa đã tồn tại trước V23) có danh sách rỗng, không phải null - Review Focus #9. */
    @Test
    void aCourseWithoutAnyColumnHasAnEmptyListNotNull() {
        var saved = courses.saveAndFlush(new Course("GC-EMPTY", "Chưa cấu hình", null, null, null));

        assertThat(courses.findById(saved.getId()).orElseThrow().getGradeColumns()).isEmpty();
    }
}
