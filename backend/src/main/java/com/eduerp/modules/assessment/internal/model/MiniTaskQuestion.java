package com.eduerp.modules.assessment.internal.model;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/** Một câu trắc nghiệm. Phương án lưu ở bảng con theo đúng thứ tự ({@code option_index}). */
@Entity
@Table(name = "mini_task_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniTaskQuestion {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mini_task_id", nullable = false, updatable = false)
    private MiniTask miniTask;

    @Column(nullable = false, length = AssessmentConstants.Limits.MAX_QUESTION_LENGTH)
    private String content;

    /** Nạp phương án theo LÔ cho nhiều câu cùng lúc - không một câu SELECT cho mỗi câu hỏi. */
    @ElementCollection
    @BatchSize(size = AssessmentConstants.Limits.MAX_QUESTIONS)
    @CollectionTable(name = "mini_task_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "content", nullable = false, length = AssessmentConstants.Limits.MAX_OPTION_LENGTH)
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_option_index", nullable = false)
    private int correctOptionIndex;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    MiniTaskQuestion(MiniTask miniTask, String content, List<String> options, int correctOptionIndex,
            int displayOrder) {
        this.miniTask = miniTask;
        this.content = content;
        this.options = new ArrayList<>(options);
        this.correctOptionIndex = correctOptionIndex;
        this.displayOrder = displayOrder;
    }
}
