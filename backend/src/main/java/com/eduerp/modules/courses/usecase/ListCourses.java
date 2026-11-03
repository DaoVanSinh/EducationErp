package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.CourseResponse;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.shared.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
    public PageResponse<CourseResponse> execute(Pageable pageable, UUID branchId) {
        return PageResponse.of(pageOf(pageable, branchId).map(ListCourses::toResponse));
    }

    /** Khoá dùng chung toàn tổ chức, nên "khoá của chi nhánh" là khoá có lớp ở đó; lớp đã huỷ không tính. */
    private Page<Course> pageOf(Pageable pageable, UUID branchId) {
        return branchId == null ? courses.findAll(pageable)
                : courses.findAllWithClassAtBranch(branchId, CoursesConstants.ClassStatus.CANCELLED, pageable);
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
