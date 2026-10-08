package com.eduerp.modules.courses.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CourseGradeColumnWeightsInvalidException;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Review Focus #6: luật tổng = 100% phải chặn ở CẢ tạo mới VÀ sửa, không chỉ lúc tạo. */
class CourseGradeColumnUseCasesTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateCourse createCourse = new CreateCourse(courses, events);
    private final UpdateCourse updateCourse = new UpdateCourse(courses, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    private static final List<GradeColumnSlot> VALID_COLUMNS = List.of(
            new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
            new GradeColumnSlot("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1));

    private static final List<GradeColumnSlot> BROKEN_COLUMNS = List.of(
            new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
            new GradeColumnSlot("Cuối kỳ", new BigDecimal("50.00"), new BigDecimal("10.00"), 1));

    private CreateCourseRequest createRequest(List<GradeColumnSlot> gradeColumns) {
        return new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", null, 24, null, gradeColumns);
    }

    private UpdateCourseRequest updateRequest(List<GradeColumnSlot> gradeColumns) {
        return new UpdateCourseRequest("Tiếng Anh giao tiếp", null, 24, null, true, gradeColumns);
    }

    private void stubNoCodeClash() {
        when(courses.findByCode("TA-GT")).thenReturn(Optional.empty());
        when(courses.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Course existingCourseWithOneColumn() {
        var course = new Course("TA-GT", "Tiếng Anh giao tiếp", null, 24, null);
        course.addGradeColumn("Cũ", new BigDecimal("100.00"), new BigDecimal("10.00"), 0);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        return course;
    }

    @Test
    void createStoresTheConfiguredGradeColumns() {
        stubNoCodeClash();

        createCourse.execute(actorAccountId, actorBranchId, createRequest(VALID_COLUMNS));

        var saved = org.mockito.ArgumentCaptor.forClass(Course.class);
        org.mockito.Mockito.verify(courses).save(saved.capture());
        assertThat(saved.getValue().getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    @Test
    void createRejectsGradeColumnsThatDoNotSumToOneHundred() {
        when(courses.findByCode("TA-GT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createCourse.execute(actorAccountId, actorBranchId, createRequest(BROKEN_COLUMNS)))
                .isInstanceOf(CourseGradeColumnWeightsInvalidException.class)
                .hasMessageContaining("90.00");
        org.mockito.Mockito.verify(courses, org.mockito.Mockito.never()).save(any(Course.class));
    }

    /** Review Focus #6: chặn cả khi SỬA. Trước khi có task này, một khóa hợp lệ có thể bị PATCH thành
     * cấu hình 90% mà không ai cản. */
    @Test
    void updateRejectsGradeColumnsThatDoNotSumToOneHundred() {
        var course = existingCourseWithOneColumn();

        assertThatThrownBy(() -> updateCourse.execute(courseId, actorAccountId, actorBranchId,
                updateRequest(BROKEN_COLUMNS)))
                .isInstanceOf(CourseGradeColumnWeightsInvalidException.class);
        assertThat(course.getGradeColumns()).as("cấu hình cũ không được chạm tới khi PATCH bị chặn")
                .extracting(CourseGradeColumn::getName).containsExactly("Cũ");
    }

    /** Spec mục 4: PATCH thay TOÀN BỘ danh sách, mirror {@code UpdateClass} với lịch học. */
    @Test
    void updateReplacesTheWholeColumnListInsteadOfPatchingRows() {
        var course = existingCourseWithOneColumn();

        updateCourse.execute(courseId, actorAccountId, actorBranchId, updateRequest(VALID_COLUMNS));

        assertThat(course.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    /** Review Focus #9: khóa học cũ chưa cấu hình cột điểm vẫn phải sửa được tên/học phí như trước. */
    @Test
    void updateAcceptsAnEmptyGradeColumnList() {
        var course = existingCourseWithOneColumn();

        assertThatCode(() -> updateCourse.execute(courseId, actorAccountId, actorBranchId,
                updateRequest(List.of()))).doesNotThrowAnyException();
        assertThat(course.getGradeColumns()).isEmpty();
    }

    @Test
    void createAcceptsAnEmptyGradeColumnList() {
        stubNoCodeClash();

        assertThatCode(() -> createCourse.execute(actorAccountId, actorBranchId, createRequest(List.of())))
                .doesNotThrowAnyException();
    }
}
