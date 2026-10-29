package com.eduerp.modules.assessment.internal.model;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Tình trạng nộp và điểm của một lượt ghi danh ở một bài. {@code enrollmentId} là UUID trần (rule #3). */
@Entity
@Table(name = "assignment_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignmentResult {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private Assignment assignment;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentConstants.SubmissionStatus status;

    private BigDecimal score;

    @Column(length = AssessmentConstants.Limits.MAX_FEEDBACK_LENGTH)
    private String feedback;

    @Column(name = "updated_by_account_id", nullable = false)
    private UUID updatedByAccountId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    AssignmentResult(Assignment assignment, UUID enrollmentId, AssessmentConstants.SubmissionStatus status,
            BigDecimal score, String feedback, UUID updatedByAccountId) {
        this.assignment = assignment;
        this.enrollmentId = enrollmentId;
        change(status, score, feedback, updatedByAccountId);
    }

    void change(AssessmentConstants.SubmissionStatus status, BigDecimal score, String feedback,
            UUID updatedByAccountId) {
        this.status = status;
        this.score = score;
        this.feedback = feedback;
        this.updatedByAccountId = updatedByAccountId;
        this.updatedAt = Instant.now();
    }
}
