package com.eduerp.modules.courses;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.ClassTeacherAssignment;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.ClassTeacherAssignmentRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

        /** Chỉ nhận học viên trước khai giảng: vào giữa khoá sẽ mất phần kiến thức của các buổi đã qua. */
        public boolean acceptsEnrollment() {
            return status == CoursesConstants.ClassStatus.ENROLLING;
        }

        /** Lớp chưa đóng (đang tuyển sinh hoặc đang học): còn giao bài được, học viên còn buổi học để trùng lịch. */
        public boolean isOpen() {
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

    /**
     * Ảnh chụp một lớp đang tuyển sinh cho trang giới thiệu công khai (Landing Page). {@code startDate} để khách
     * biết ngày khai giảng; {@code activeEnrollments} để tính chỗ trống.
     */
    public record ActiveClassSnapshot(UUID id, String code, UUID courseId, String courseName,
            UUID branchId, UUID teacherId, int maxSeats, List<WeeklyScheduleSlot> schedule,
            CoursesConstants.ClassStatus status, LocalDate startDate, long activeEnrollments) {
    }

    /** Lớp chưa đóng - giáo viên của các lớp này được coi là đang dạy ở chi nhánh của lớp. */
    private static final List<CoursesConstants.ClassStatus> OPEN_STATUSES = List.of(
            CoursesConstants.ClassStatus.ENROLLING, CoursesConstants.ClassStatus.IN_PROGRESS);

    /** Trạng thái lớp còn nhận học viên - cùng quy tắc {@link ClassInfoResponse#acceptsEnrollment()}. */
    private static final List<CoursesConstants.ClassStatus> ENROLLING_STATUSES = List.of(
            CoursesConstants.ClassStatus.ENROLLING);

    private final CourseRepository courses;
    private final ClassRepository classes;
    private final ClassRosterPort roster;
    private final ClassTeacherAssignmentRepository assignments;

    CoursesManagement(CourseRepository courses, ClassRepository classes, ClassRosterPort roster,
            ClassTeacherAssignmentRepository assignments) {
        this.assignments = assignments;
        this.courses = courses;
        this.classes = classes;
        this.roster = roster;
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

    /** Giống {@link #getClassInfo(UUID)} nhưng theo lô; id không tồn tại chỉ vắng mặt trong kết quả, không ném. */
    @Transactional(readOnly = true)
    public List<ClassInfoResponse> classInfosOf(Collection<UUID> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return classes.findAllById(classIds).stream().map(CoursesManagement::toClassInfo).toList();
    }

    /** Các lớp đang tuyển sinh của một khóa - {@code modules.enrollment} dùng để gợi ý lớp thay thế khi trùng lịch. */
    @Transactional(readOnly = true)
    public List<ClassInfoResponse> listEnrollingClassesOfCourse(UUID courseId) {
        return classes.findAllByCourse_IdAndStatusIn(courseId, ENROLLING_STATUSES).stream()
                .map(CoursesManagement::toClassInfo)
                .toList();
    }

    /** Tài khoản giáo viên đang dạy lớp còn mở ở một chi nhánh - để lọc danh sách giáo viên theo chi nhánh. */
    @Transactional(readOnly = true)
    public List<UUID> teacherIdsTeachingAt(UUID branchId) {
        return classes.findTeacherIdsAtBranch(branchId, OPEN_STATUSES);
    }

    /** Số lớp còn mở của một giáo viên - phải bàn giao hết trước khi cho nghỉ. */
    @Transactional(readOnly = true)
    public long openClassCountOf(UUID teacherId) {
        return classes.countByTeacherIdAndStatusIn(teacherId, OPEN_STATUSES);
    }

    /**
     * Ai dạy lớp vào một ngày: người được phân công có hiệu lực từ ngày đó trở về trước. Ngày trước mốc đầu tiên thì lấy
     * người đầu tiên. Rỗng khi lớp chưa có dòng lịch sử nào, nơi gọi dùng giáo viên hiện tại.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> teacherOn(UUID classId, LocalDate date) {
        return assignments.findFirstByClassIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(classId, date)
                .or(() -> assignments.findFirstByClassIdOrderByEffectiveFromAsc(classId))
                .map(ClassTeacherAssignment::getTeacherId);
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

    /**
     * Tên khóa học theo lô, cho các màn hình hoá đơn và đăng ký nhóm chỉ biết {@code courseId}. Id không
     * tồn tại chỉ vắng mặt trong map, không ném - mirror {@link #classSummariesOf(Collection)}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> courseNamesOf(Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courses.findAllById(courseIds).stream().collect(Collectors.toMap(Course::getId, Course::getName));
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
    public List<ActiveClassSnapshot> listEnrollingClasses() {
        var open = classes.findAllByStatusInOrderByCodeAsc(ENROLLING_STATUSES).stream()
                .filter(cls -> cls.getCourse().isActive())
                .toList();
        // Một câu đếm cho cả danh sách, không N câu - trang chủ là trang công khai, tải nhiều nhất.
        var enrolled = roster.countActiveByClass(open.stream().map(Class::getId).toList());
        return open.stream()
                .map(cls -> new ActiveClassSnapshot(cls.getId(), cls.getCode(), cls.getCourse().getId(),
                        cls.getCourse().getName(), cls.getBranchId(), cls.getTeacherId(), cls.getMaxSeats(),
                        cls.getSchedule().stream()
                                .map(slot -> new WeeklyScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(),
                                        slot.getEndTime()))
                                .toList(),
                        cls.getStatus(), cls.getStartDate(), enrolled.getOrDefault(cls.getId(), 0L)))
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
