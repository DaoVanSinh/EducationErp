package com.eduerp.modules.assessment.internal.model;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Bộ câu hỏi trắc nghiệm của một lớp để học viên tự ôn luyện. {@code classId} là UUID trần (rule #3). */
@Entity
@Table(name = "mini_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniTask {

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

    @Column(nullable = false)
    private boolean active;

    /** Ngưỡng "Đạt" (% câu đúng) - điểm cao nhất của học viên chạm mức này là đã thành thạo. */
    @Column(name = "pass_percent", nullable = false)
    private int passPercent;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "miniTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<MiniTaskQuestion> questions = new ArrayList<>();

    public MiniTask(UUID classId, String title, String description, int passPercent, UUID createdByAccountId) {
        this.classId = classId;
        this.title = title;
        this.description = description;
        this.passPercent = passPercent;
        this.active = true;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String description, int passPercent, boolean active) {
        this.title = title;
        this.description = description;
        this.passPercent = passPercent;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    /** Thay TOÀN BỘ câu hỏi: xoá danh sách cũ (orphanRemoval) rồi thêm lại theo thứ tự mới. */
    public void clearQuestions() {
        questions.clear();
    }

    public void addQuestion(String content, List<String> options, int correctOptionIndex, String explanation) {
        questions.add(new MiniTaskQuestion(this, content, options, correctOptionIndex, explanation, questions.size()));
    }
}
