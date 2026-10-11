package com.eduerp.modules.courses;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code EnrollmentExceptionTest}: errorCode là hợp đồng với frontend, đổi là breaking. */
class CoursesExceptionTest {

    @Test
    void courseNotFoundIsNotFound() {
        var ex = new CourseNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_COURSE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void classNotFoundIsNotFound() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Review Focus #6: lỗi phải nói ra tổng thật vừa nhập, nếu không người dùng không biết sai bao nhiêu. */
    @Test
    void gradeColumnWeightsInvalidIsBadRequestAndNamesTheActualTotal() {
        var ex = new CourseGradeColumnWeightsInvalidException(new BigDecimal("95.50"));
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_GRADE_COLUMN_WEIGHTS_INVALID");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("95.50", "100");
    }
}
