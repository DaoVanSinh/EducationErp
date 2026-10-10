package com.eduerp.modules.courses;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module courses — type DUY NHẤT mà module khác được phép gọi (rule #1).
 * {@code modules.enrollment} đọc {@link ClassInfoResponse} để chốt snapshot lúc ghi danh,
 * {@code modules.billing} đọc {@link CourseTuitionResponse} để chặn tổng các đợt vượt học phí,
 * {@code modules.academic} đọc {@link ClassInfoResponse} (kiểm giáo viên được phân công + đếm số
 * buổi đã học) và {@link #getGradeColumns(UUID)} (cấu trúc điểm của khóa).
 */
@Service
public class CoursesManagement {

    /**
     * Ảnh chụp những gì module khác cần biết về một lớp - không lộ entity Class ra ngoài.
     *
     * <p>{@code teacherId}, {@code standardSessionCount} và {@code schedule} thêm ở Phân hệ 4 (spec
     * mục 4): gộp cả ba vào đây để {@code modules.academic} chỉ cần MỘT lần gọi facade là đủ dữ liệu
     * tính tiến trình, không phải gọi thêm {@link #getCourseTuition(UUID)} cho mục đích không liên
     * quan tới học phí. {@code schedule} dùng lại {@link WeeklyScheduleSlot} của {@code dto} thay vì
     * định nghĩa một record y hệt - một khái niệm, một kiểu.
     */
    public record ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, UUID teacherId, int maxSeats,
            boolean active, Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {
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
        return classes.findById(classId).map(CoursesManagement::toClassInfo);
    }

    @Transactional(readOnly = true)
    public Optional<CourseTuitionResponse> getCourseTuition(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> new CourseTuitionResponse(course.getId(), course.getTuitionFee(), course.isActive()));
    }

    /**
     * Cấu trúc điểm của một khóa học, theo {@code displayOrder} (spec mục 4). Khóa không tồn tại trả
     * danh sách RỖNG chứ không ném: nơi gọi đã có lỗi riêng cho "không tìm thấy", và "khóa chưa cấu
     * hình cột điểm" cũng là danh sách rỗng - hai tình huống dẫn tới cùng một kết luận nghiệp vụ
     * ("chưa có cột điểm nào để nhập").
     */
    @Transactional(readOnly = true)
    public List<GradeColumnResponse> getGradeColumns(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> course.getGradeColumns().stream()
                        .map(column -> new GradeColumnResponse(column.getId(), column.getName(),
                                column.getWeightPercent(), column.getMaxScore(), column.getDisplayOrder()))
                        .toList())
                .orElseGet(List::of);
    }

    private static ClassInfoResponse toClassInfo(Class cls) {
        var schedule = cls.getSchedule().stream()
                .map(slot -> new WeeklyScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
        return new ClassInfoResponse(cls.getId(), cls.getCourse().getId(), cls.getBranchId(),
                cls.getTeacherId(), cls.getMaxSeats(), cls.isActive(),
                cls.getCourse().getStandardSessionCount(), schedule);
    }
}
