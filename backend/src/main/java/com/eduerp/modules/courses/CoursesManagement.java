package com.eduerp.modules.courses;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
            int minSeats, CoursesConstants.ClassStatus status, LocalDate startDate, LocalDate endDate,
            Instant startedAt, Instant endedAt, Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {

        /** Lớp còn nhận ghi danh: đang tuyển sinh hoặc đang học (cho phép vào học muộn). */
        public boolean acceptsEnrollment() {
            return status.isOpen();
        }

        public boolean isInProgress() {
            return status == CoursesConstants.ClassStatus.IN_PROGRESS;
        }
    }

    /** {@code tuitionFee} null = khoá học chưa chốt giá (khác với "không tìm thấy khoá học"). */
    public record CourseTuitionResponse(UUID courseId, BigDecimal tuitionFee, boolean active) {
    }

    /** Ảnh chụp một lớp cho mục đích HIỂN THỊ (mã lớp + tên khóa học) - khác {@link ClassInfoResponse}
     * (dùng để tính toán nghiệp vụ). Cho trang chi tiết học viên ghép tên lớp vào bảng kết quả học
     * tập. */
    public record ClassBasicSummary(UUID classId, String code, UUID courseId, String courseName) {
    }

    /** Ảnh chụp một khóa học đang mở cho trang giới thiệu công khai (Landing Page). */
    public record ActiveCourseSnapshot(UUID id, String code, String name, String description,
            Integer standardSessionCount, BigDecimal tuitionFee, List<GradeColumnResponse> gradeColumns) {
    }

    /** Ảnh chụp một lớp đang hoạt động cho trang giới thiệu công khai (Landing Page). */
    public record ActiveClassSnapshot(UUID id, String code, UUID courseId, String courseName,
            UUID branchId, UUID teacherId, int maxSeats, List<WeeklyScheduleSlot> schedule) {
    }

    /** Trạng thái lớp được coi là "đang hoạt động" để hiện ở trang chủ - cùng quy tắc {@link CoursesConstants.ClassStatus#isOpen()}. */
    private static final List<CoursesConstants.ClassStatus> OPEN_CLASS_STATUSES = Arrays.stream(
            CoursesConstants.ClassStatus.values()).filter(CoursesConstants.ClassStatus::isOpen).toList();

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

    /**
     * Mã lớp + tên khóa học theo lô, cho {@code modules.academic.GetStudentAcademicSummary} ghép
     * vào bảng kết quả học tập của học viên - mirror {@code IdentityManagement.summariesOf}. Id
     * không tồn tại chỉ vắng mặt trong map, không ném.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ClassBasicSummary> classSummariesOf(Collection<UUID> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classes.findAllById(classIds).stream()
                .collect(Collectors.toMap(Class::getId, cls -> new ClassBasicSummary(cls.getId(), cls.getCode(),
                        cls.getCourse().getId(), cls.getCourse().getName())));
    }

    @Transactional(readOnly = true)
    public List<ActiveCourseSnapshot> listActiveCourses() {
        return courses.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(course -> new ActiveCourseSnapshot(course.getId(), course.getCode(), course.getName(),
                        course.getDescription(), course.getStandardSessionCount(), course.getTuitionFee(),
                        course.getGradeColumns().stream()
                                .map(col -> new GradeColumnResponse(col.getId(), col.getName(),
                                        col.getWeightPercent(), col.getMaxScore(), col.getDisplayOrder()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActiveClassSnapshot> listActiveClasses() {
        return classes.findAllByStatusInOrderByCodeAsc(OPEN_CLASS_STATUSES).stream()
                .filter(cls -> cls.getCourse().isActive())
                .map(cls -> new ActiveClassSnapshot(cls.getId(), cls.getCode(), cls.getCourse().getId(),
                        cls.getCourse().getName(), cls.getBranchId(), cls.getTeacherId(), cls.getMaxSeats(),
                        cls.getSchedule().stream()
                                .map(slot -> new WeeklyScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(),
                                        slot.getEndTime()))
                                .toList()))
                .toList();
    }

    private static ClassInfoResponse toClassInfo(Class cls) {
        var schedule = cls.getSchedule().stream()
                .map(slot -> new WeeklyScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
        return new ClassInfoResponse(cls.getId(), cls.getCourse().getId(), cls.getBranchId(),
                cls.getTeacherId(), cls.getMaxSeats(), cls.getMinSeats(), cls.getStatus(), cls.getStartDate(),
                cls.getEndDate(), cls.getStartedAt(), cls.getEndedAt(),
                cls.getCourse().getStandardSessionCount(), schedule);
    }
}
