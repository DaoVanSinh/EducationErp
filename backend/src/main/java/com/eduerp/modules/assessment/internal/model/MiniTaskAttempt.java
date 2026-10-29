package com.eduerp.modules.assessment.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Một lượt làm mini-task. Lưu số câu đúng/tổng số câu tại thời điểm nộp (snapshot): giáo viên sửa câu hỏi
 * về sau không làm đổi kết quả đã chấm. Bất biến - không có setter.
 */
@Entity
@Table(name = "mini_task_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniTaskAttempt {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "mini_task_id", nullable = false, updatable = false)
    private UUID miniTaskId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "correct_count", nullable = false, updatable = false)
    private int correctCount;

    @Column(name = "total_questions", nullable = false, updatable = false)
    private int totalQuestions;

    @Column(name = "score_percent", nullable = false, updatable = false)
    private int scorePercent;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    public MiniTaskAttempt(UUID miniTaskId, UUID enrollmentId, int correctCount, int totalQuestions,
            int scorePercent) {
        this.miniTaskId = miniTaskId;
        this.enrollmentId = enrollmentId;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.scorePercent = scorePercent;
        this.submittedAt = Instant.now();
    }
}
