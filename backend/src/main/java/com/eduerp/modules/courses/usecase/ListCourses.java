package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.dto.CourseResponse;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.shared.PageResponse;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListCourses {

    private final CourseRepository courses;

    ListCourses(CourseRepository courses) {
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> execute(Pageable pageable) {
        return PageResponse.of(courses.findAll(pageable).map(ListCourses::toResponse));
    }

    private static CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getName(),
                course.getDescription(), course.getStandardSessionCount(), course.getTuitionFee(),
                course.isActive(), toGradeColumnResponses(course.getGradeColumns()));
    }

    private static List<GradeColumnResponse> toGradeColumnResponses(List<CourseGradeColumn> columns) {
        return columns.stream()
                .map(column -> new GradeColumnResponse(column.getId(), column.getName(), column.getWeightPercent(),
                        column.getMaxScore(), column.getDisplayOrder()))
                .toList();
    }
}
