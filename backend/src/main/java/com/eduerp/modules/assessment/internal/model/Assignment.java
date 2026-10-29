package com.eduerp.modules.assessment.internal.model;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Bài tập / mini-test giao cho một lớp. {@code classId} là UUID trần (rule #3). */
@Entity
@Table(name = "assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assignment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(nullable = false, length = AssessmentConstants.Limits.MAX_TITLE_LENGTH)
    private String title;

    @Column(length = AssessmentConstants.Limits.MAX_DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssessmentConstants.AssignmentType type;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssignmentResult> results = new ArrayList<>();

    public Assignment(UUID classId, String title, String description, AssessmentConstants.AssignmentType type,
            LocalDate dueDate, BigDecimal maxScore, UUID createdByAccountId) {
        this.classId = classId;
        this.title = title;
        this.description = description;
        this.type = type;
        this.dueDate = dueDate;
        this.maxScore = maxScore;
        this.active = true;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
    }

    public void update(String title, String description, AssessmentConstants.AssignmentType type,
            LocalDate dueDate, BigDecimal maxScore, boolean active) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.dueDate = dueDate;
        this.maxScore = maxScore;
        this.active = active;
    }

    /** Upsert kết quả của một học viên - UNIQUE (assignment_id, enrollment_id) của V29. */
    public void recordResult(UUID enrollmentId, AssessmentConstants.SubmissionStatus status, BigDecimal score,
            String feedback, UUID actorAccountId) {
        results.stream()
                .filter(result -> result.getEnrollmentId().equals(enrollmentId))
                .findFirst()
                .ifPresentOrElse(result -> result.change(status, score, feedback, actorAccountId),
                        () -> results.add(new AssignmentResult(this, enrollmentId, status, score, feedback,
                                actorAccountId)));
    }
}
