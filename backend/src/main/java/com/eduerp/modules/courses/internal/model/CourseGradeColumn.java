package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một cột điểm của một khóa học ("Giữa kỳ 40%", "Cuối kỳ 60%"). {@code course} là quan hệ JPA thật -
 * {@code Course} cùng module (rule #3 chỉ cấm xuyên module), mirror {@code ClassSchedule.parentClass}.
 */
@Entity
@Table(name = "course_grade_columns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseGradeColumn {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String name;

    @Column(name = "weight_percent", nullable = false)
    private BigDecimal weightPercent;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    CourseGradeColumn(Course course, String name, BigDecimal weightPercent, BigDecimal maxScore,
            int displayOrder) {
        this.course = course;
        this.name = name;
        this.weightPercent = weightPercent;
        this.maxScore = maxScore;
        this.displayOrder = displayOrder;
    }

    /**
     * Sửa tại chỗ thay vì xoá-và-tạo-lại: giữ nguyên {@code id} để {@code grade_entries} đã tham
     * chiếu cột này (FK, V24) không bị mồ côi (Final review Critical #1).
     */
    void update(String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder) {
        this.name = name;
        this.weightPercent = weightPercent;
        this.maxScore = maxScore;
        this.displayOrder = displayOrder;
    }
}
