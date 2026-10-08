package com.eduerp.modules.courses;

import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module courses — type DUY NHẤT mà module khác được phép gọi (rule #1).
 * {@code modules.enrollment} đọc {@link ClassInfoResponse} để chốt snapshot lúc ghi danh,
 * {@code modules.billing} đọc {@link CourseTuitionResponse} để chặn tổng các đợt vượt học phí.
 */
@Service
public class CoursesManagement {

    /** Ảnh chụp những gì modules.enrollment cần biết về một lớp - không lộ entity Class ra ngoài. */
    public record ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, int maxSeats, boolean active) {
    }

    /** {@code tuitionFee} null = khoá học chưa chốt giá (khác với "không tìm thấy khoá học"). */
    public record CourseTuitionResponse(UUID courseId, BigDecimal tuitionFee, boolean active) {
    }

    private final CourseRepository courses;
    private final ClassRepository classes;

    CoursesManagement(CourseRepository courses, ClassRepository classes) {
        this.courses = courses;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    public boolean courseExists(UUID courseId) {
        return courses.existsById(courseId);
    }

    @Transactional(readOnly = true)
    public boolean classExists(UUID classId) {
        return classes.existsById(classId);
    }

    @Transactional(readOnly = true)
    public Optional<ClassInfoResponse> getClassInfo(UUID classId) {
        return classes.findById(classId).map(cls -> new ClassInfoResponse(cls.getId(), cls.getCourse().getId(),
                cls.getBranchId(), cls.getMaxSeats(), cls.isActive()));
    }

    @Transactional(readOnly = true)
    public Optional<CourseTuitionResponse> getCourseTuition(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> new CourseTuitionResponse(course.getId(), course.getTuitionFee(), course.isActive()));
    }
}
