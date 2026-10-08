package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String description;

    @Setter
    private Integer standardSessionCount;

    /** Học phí toàn khoá, VND, scale 0. Nullable - khoá học có thể chưa chốt giá (spec mục 5). */
    @Setter
    @Column(name = "tuition_fee")
    private BigDecimal tuitionFee;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    // Cùng lý do với Class.schedule: liệt kê một trang khóa học mà không batch thì mỗi khóa tự bắn
    // một câu SELECT cột điểm riêng (N+1), xem ListCourses.
    @BatchSize(size = 50)
    private final List<CourseGradeColumn> gradeColumns = new ArrayList<>();

    public Course(String code, String name, String description, Integer standardSessionCount,
            BigDecimal tuitionFee) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.standardSessionCount = standardSessionCount;
        this.tuitionFee = tuitionFee;
        this.active = true;
    }

    public List<CourseGradeColumn> getGradeColumns() {
        return List.copyOf(gradeColumns);
    }

    public void addGradeColumn(String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder) {
        gradeColumns.add(new CourseGradeColumn(this, name, weightPercent, maxScore, displayOrder));
    }

    /** Gọi trước khi thêm lại cột mới khi sửa khóa học - PATCH thay toàn bộ danh sách, không vá từng
     * dòng (spec mục 4, mirror {@code Class.clearSchedule}). */
    public void clearGradeColumns() {
        gradeColumns.clear();
    }

    /**
     * PATCH thay danh sách cột điểm theo spec mục 4, nhưng KHÔNG xoá-và-tạo-lại như
     * {@code clearGradeColumns()}+{@code addGradeColumn(...)}: {@code course_grade_columns} bị
     * {@code grade_entries} tham chiếu bằng khoá ngoại (V24), nên xoá một cột đã có điểm sẽ vỡ FK,
     * và ngay cả khi không vỡ thì cột mới mang id mới sẽ làm mồ côi điểm đã nhập cho cột cũ (Final
     * review Critical #1).
     *
     * <p>Cột có {@code id} khớp một cột đang có được SỬA TẠI CHỖ (giữ nguyên id); cột không còn
     * trong danh sách bị xoá (chỉ an toàn nếu chưa có điểm nào - FK sẽ chặn ở tầng flush nếu không,
     * xem {@code UpdateCourse}); cột có {@code id} null là cột MỚI.
     */
    public void reconcileGradeColumns(List<GradeColumnSlot> slots) {
        var keepIds = slots.stream().map(GradeColumnSlot::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        gradeColumns.removeIf(column -> !keepIds.contains(column.getId()));
        var byId = gradeColumns.stream()
                .collect(Collectors.toMap(CourseGradeColumn::getId, Function.identity()));
        for (var slot : slots) {
            var existing = slot.id() == null ? null : byId.get(slot.id());
            if (existing != null) {
                existing.update(slot.name(), slot.weightPercent(), slot.maxScore(), slot.displayOrder());
            } else {
                addGradeColumn(slot.name(), slot.weightPercent(), slot.maxScore(), slot.displayOrder());
            }
        }
    }
}
