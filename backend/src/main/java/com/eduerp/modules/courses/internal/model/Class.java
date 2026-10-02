package com.eduerp.modules.courses.internal.model;

import com.eduerp.modules.courses.CoursesConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
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

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    @OneToMany(mappedBy = "parentClass", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    // Gộp lazy-load của nhiều Class thành một câu IN duy nhất khi liệt kê một trang - không batch thì
    // mỗi lớp trong trang tự bắn một câu SELECT lịch học riêng (N+1), xem ListClasses.
    @BatchSize(size = 50)
    private final List<ClassSchedule> schedule = new ArrayList<>();

    public Class(Course course, String code, UUID branchId, UUID teacherId, int maxSeats) {
        this.course = course;
        this.code = code;
        this.branchId = branchId;
        this.teacherId = teacherId;
        this.maxSeats = maxSeats;
        this.active = true;
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
}
