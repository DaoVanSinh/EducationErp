package com.eduerp.modules.academic.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Điểm của một học viên ở một cột điểm của khóa. {@code enrollmentId}/{@code courseGradeColumnId}/
 * {@code enteredByAccountId} là UUID trần - {@code enrollments}/{@code courses}/{@code identity}
 * thuộc module khác nên không có {@code @ManyToOne} (rule #3).
 *
 * <p>{@code enteredAt} bất biến, {@code updatedAt} đổi mỗi lần sửa: nhật ký "ai nhập/sửa điểm lúc
 * nào" là lý do duy nhất hai cột này tồn tại.
 */
@Entity
@Table(name = "grade_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "course_grade_column_id", nullable = false, updatable = false)
    private UUID courseGradeColumnId;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(name = "entered_by_account_id", nullable = false)
    private UUID enteredByAccountId;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GradeEntry(UUID enrollmentId, UUID courseGradeColumnId, BigDecimal score,
            UUID enteredByAccountId) {
        this.enrollmentId = enrollmentId;
        this.courseGradeColumnId = courseGradeColumnId;
        this.score = score;
        this.enteredByAccountId = enteredByAccountId;
        this.enteredAt = Instant.now();
        this.updatedAt = this.enteredAt;
    }

    /** Nhập lại điểm một cột là SỬA dòng cũ (UNIQUE của V24), nên {@code enteredAt} giữ nguyên và
     * {@code enteredByAccountId} chuyển sang người sửa gần nhất. */
    public void changeScore(BigDecimal score, UUID enteredByAccountId) {
        this.score = score;
        this.enteredByAccountId = enteredByAccountId;
        this.updatedAt = Instant.now();
    }
}
