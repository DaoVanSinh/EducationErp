package com.eduerp.modules.courses.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Course;
import java.math.BigDecimal;
import java.util.List;
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
        courses.save(new Course("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24, null));

        var found = courses.findByCode("TA-GT");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tiếng Anh giao tiếp");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    void savesACourseWithATuitionFee() {
        courses.save(new Course("TA-IELTS", "IELTS 6.5", "Mô tả", 36, new BigDecimal("12000000")));

        var found = courses.findByCode("TA-IELTS").orElseThrow();

        assertThat(found.getTuitionFee()).isEqualByComparingTo(new BigDecimal("12000000"));
    }

    /** Học phí để trống được: khoá học cũ chưa gắn giá vẫn phải lưu/đọc bình thường. */
    @Test
    void savesACourseWithoutATuitionFee() {
        courses.save(new Course("TA-NOFEE", "Chưa gắn giá", null, null, null));

        assertThat(courses.findByCode("TA-NOFEE").orElseThrow().getTuitionFee()).isNull();
    }

    private Course saveSearchableCourse(String code, String name, String description, boolean active) {
        var course = new Course(code, name, description, 24, new BigDecimal("5000000"));
        course.setActive(active);
        return courses.saveAndFlush(course);
    }

    private List<String> searchCodes(String query) {
        return courses.searchActiveByFullText(query, 10).stream().map(Course::getCode).toList();
    }

    /** Tiếng Việt có dấu phải khớp nguyên dạng - cấu hình 'simple' không stem, không bỏ dấu
     * (spec Ruling R1). */
    @Test
    void matchesAVietnameseKeywordWithDiacritics() {
        saveSearchableCourse("FTS-TA", "Tiếng Anh giao tiếp buổi tối", "Luyện phản xạ nói", true);
        saveSearchableCourse("FTS-TOAN", "Toán 9 nâng cao", "Ôn thi vào 10", true);

        assertThat(searchCodes("tiếng Anh")).containsExactly("FTS-TA");
    }

    /**
     * Quyết định kỹ thuật #2 của plan. Với plainto_tsquery nguyên bản (nối mọi từ bằng &), câu hỏi
     * này trả về RỖNG vì không khóa nào chứa đủ cả "có", "không", "buổi"... - tức mọi câu hỏi tự
     * nhiên đều rỗng và cả phân hệ vô dụng. Test này là chỗ duy nhất chứng minh ngữ nghĩa OR.
     */
    @Test
    void matchesANaturalLanguageQuestionWithExtraWords() {
        saveSearchableCourse("FTS-TA2", "Tiếng Anh giao tiếp buổi tối", "Luyện phản xạ nói", true);

        assertThat(searchCodes("có khóa tiếng anh giao tiếp buổi tối nào không")).contains("FTS-TA2");
    }

    /** Review Focus #3 ở tầng thấp nhất: điều kiện active = true nằm trong chính câu SQL, không phải
     * một lượt filter ở Java có thể bị quên. */
    @Test
    void neverReturnsAnInactiveCourseEvenWhenItMatches() {
        saveSearchableCourse("FTS-OFF", "Tiếng Nhật sơ cấp", "Đã đóng khóa", false);
        saveSearchableCourse("FTS-ON", "Tiếng Nhật trung cấp", "Đang mở", true);

        assertThat(searchCodes("tiếng Nhật")).containsExactly("FTS-ON");
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() {
        saveSearchableCourse("FTS-NONE", "Tiếng Hàn sơ cấp", "Mô tả", true);

        assertThat(searchCodes("lập trình nhúng")).isEmpty();
    }

    /** Review Focus #10: câu hỏi chỉ gồm dấu câu phải ra danh sách rỗng, KHÔNG được ném lỗi SQL -
     * plainto_tsquery trả tsquery rỗng và chuỗi rỗng vẫn cast được về tsquery. */
    @Test
    void returnsAnEmptyListForAQuestionMadeOfPunctuationOnly() {
        saveSearchableCourse("FTS-PUNCT", "Tiếng Pháp sơ cấp", "Mô tả", true);

        assertThat(searchCodes("??? !!! ...")).isEmpty();
    }

    /** Khóa chưa có mô tả vẫn tìm được theo tên - coalesce(description, '') lo phần đó. */
    @Test
    void matchesACourseWhoseDescriptionIsNull() {
        saveSearchableCourse("FTS-NODESC", "Luyện chữ đẹp lớp 1", null, true);

        assertThat(searchCodes("luyện chữ")).containsExactly("FTS-NODESC");
    }

    /** Hạn mức truyền vào phải được tôn trọng - đây là trần chi phí token của prompt. */
    @Test
    void respectsTheRequestedLimit() {
        saveSearchableCourse("FTS-L1", "Guitar cơ bản", "Guitar", true);
        saveSearchableCourse("FTS-L2", "Guitar nâng cao", "Guitar", true);
        saveSearchableCourse("FTS-L3", "Guitar đệm hát", "Guitar", true);

        assertThat(courses.searchActiveByFullText("guitar", 2)).hasSize(2);
    }
}
