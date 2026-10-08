package com.eduerp.modules.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code BillingExceptionTest}/{@code EnrollmentExceptionTest}: errorCode là hợp đồng với
 * frontend (ApiError.errorCode), đổi là breaking. */
class AcademicExceptionTest {

    @Test
    void classNotFoundIsNotFound() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void enrollmentNotFoundIsBadRequest() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Review Focus #11: một lỗi duy nhất cho cả "cột không tồn tại" và "cột của khóa khác" - vì
     * nơi gọi chỉ tra trong danh sách cột của đúng khóa đó, hai tình huống không phân biệt được và
     * cũng không cần phân biệt; thông báo nói ra cả hai id là đủ để người nhập hiểu. */
    @Test
    void gradeColumnNotInCourseIsConflictAndNamesBothIds() {
        var columnId = UUID.randomUUID();
        var courseId = UUID.randomUUID();
        var ex = new GradeColumnNotInCourseException(columnId, courseId);
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_GRADE_COLUMN_NOT_IN_COURSE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains(columnId.toString(), courseId.toString());
    }

    /** Review Focus #10: thông báo phải nói ra thang điểm của ĐÚNG cột đó, nếu không người nhập
     * không biết 9.5 sai ở đâu khi cột này thang 5. */
    @Test
    void invalidScoreIsBadRequestAndNamesTheAllowedRange() {
        var ex = new InvalidScoreException(new BigDecimal("9.50"), new BigDecimal("5.00"));
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_INVALID_SCORE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("9.50", "5.00");
    }

    /** Review Focus #4: 403, không phải 404 - giáo viên có tồn tại và lớp có tồn tại, chỉ là không
     * phải lớp của họ. 404 ở đây sẽ che mất lý do thật. */
    @Test
    void notAssignedToClassIsForbidden() {
        var ex = new NotAssignedToClassException(UUID.randomUUID(), UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_NOT_ASSIGNED_TO_CLASS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
