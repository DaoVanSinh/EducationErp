package com.eduerp.modules.courses;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class CourseGradeColumnWeightsInvalidException extends CoursesException {

    public CourseGradeColumnWeightsInvalidException(BigDecimal actualTotal) {
        super("COURSES_GRADE_COLUMN_WEIGHTS_INVALID", HttpStatus.BAD_REQUEST,
                "Tổng trọng số các cột điểm phải bằng "
                        + CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT.toPlainString()
                        + "%, đang là " + actualTotal.toPlainString() + "%");
    }
}
