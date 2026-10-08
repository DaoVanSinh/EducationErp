package com.eduerp.modules.enrollment.internal.model;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một lần ghi danh học viên vào một lớp. {@code courseId}/{@code branchId} là UUID trần, chốt
 * snapshot lúc ghi danh - {@code classes}/{@code courses}/{@code branches} thuộc module khác nên
 * không có {@code @ManyToOne} (rule #3).
 */
@Entity
@Table(name = "enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentConstants.EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    public Enrollment(UUID studentProfileId, UUID classId, UUID courseId, UUID branchId, UUID createdByAccountId) {
        this.studentProfileId = studentProfileId;
        this.classId = classId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.createdByAccountId = createdByAccountId;
        this.status = EnrollmentConstants.EnrollmentStatus.ACTIVE;
        this.enrolledAt = Instant.now();
    }

    /** Rút khỏi lớp KHÔNG huỷ hoá đơn đã phát hành - chỉ chặn billing tạo đợt mới (spec mục 4). */
    public void withdraw() {
        this.status = EnrollmentConstants.EnrollmentStatus.WITHDRAWN;
        this.withdrawnAt = Instant.now();
    }

    public void complete() {
        this.status = EnrollmentConstants.EnrollmentStatus.COMPLETED;
    }
}
