package com.eduerp.modules.courses.internal.model;

import com.eduerp.modules.courses.CoursesConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * {@code course} là quan hệ JPA thật - {@code Course} cùng module (rule #3 chỉ cấm xuyên module).
 * {@code branchId}/{@code teacherId} là UUID trần vì {@code organization}/{@code identity} là module
 * khác.
 *
 * <p>Vòng đời (V31): trạng thái chỉ đổi qua {@link #start}, {@link #complete}, {@link #cancel} - không có
 * setter, để mọi lần chuyển trạng thái đều đi qua use case có kiểm tra luật
 * ({@code ClassLifecycleRules}).
 */
@Entity
@Table(name = "classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Class {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", updatable = false)
    private Course course;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "teacher_id", nullable = false)
    @Setter
    private UUID teacherId;

    @Column(nullable = false)
    @Setter
    private int maxSeats;

    /** Số ghi danh đang học tối thiểu để được khai giảng. */
    @Column(nullable = false)
    @Setter
    private int minSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoursesConstants.ClassStatus status;

    /** Ngày khai giảng dự kiến; sửa được khi lớp còn đang tuyển sinh. */
    @Column(nullable = false)
    @Setter
    private LocalDate startDate;

    /** Ngày kết thúc dự kiến (không bắt buộc); khi lớp kết thúc được chốt thành ngày kết thúc thật. */
    @Setter
    private LocalDate endDate;

    /** Thời điểm thật sự khai giảng - mốc bắt đầu đếm tiến trình. */
    private Instant startedAt;

    /** Thời điểm thật sự kết thúc/huỷ - mốc dừng đếm tiến trình. */
    private Instant endedAt;

    @OneToMany(mappedBy = "parentClass", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    // Gộp lazy-load của nhiều Class thành một câu IN duy nhất khi liệt kê một trang - không batch thì
    // mỗi lớp trong trang tự bắn một câu SELECT lịch học riêng (N+1), xem ListClasses.
    @BatchSize(size = 50)
    private final List<ClassSchedule> schedule = new ArrayList<>();

    public Class(Course course, String code, UUID branchId, UUID teacherId, int maxSeats, int minSeats,
            LocalDate startDate, LocalDate endDate) {
        this.course = course;
        this.code = code;
        this.branchId = branchId;
        this.teacherId = teacherId;
        this.maxSeats = maxSeats;
        this.minSeats = minSeats;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CoursesConstants.ClassStatus.ENROLLING;
    }

    public List<ClassSchedule> getSchedule() {
        return List.copyOf(schedule);
    }

    public void addSchedule(CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        schedule.add(new ClassSchedule(this, dayOfWeek, startTime, endTime));
    }

    /** Gọi trước khi thêm lại lịch mới khi sửa lớp - PATCH thay toàn bộ lịch, không vá từng dòng. */
    public void clearSchedule() {
        schedule.clear();
    }

    public void start(Instant now) {
        this.status = CoursesConstants.ClassStatus.IN_PROGRESS;
        this.startedAt = now;
    }

    /** Ngày kết thúc dự kiến được chốt thành ngày kết thúc thật nếu lớp kết thúc sớm hoặc chưa có dự kiến. */
    public void complete(Instant now, LocalDate today) {
        this.status = CoursesConstants.ClassStatus.COMPLETED;
        this.endedAt = now;
        if (endDate == null || endDate.isAfter(today)) {
            this.endDate = today.isBefore(startDate) ? startDate : today;
        }
    }

    public void cancel(Instant now) {
        this.status = CoursesConstants.ClassStatus.CANCELLED;
        this.endedAt = now;
    }
}
