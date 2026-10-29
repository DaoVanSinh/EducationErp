package com.eduerp.modules.assessment.internal.repository;

import com.eduerp.modules.assessment.internal.model.MiniTask;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiniTaskRepository extends JpaRepository<MiniTask, UUID> {

    /** Câu hỏi nạp kèm trong cùng câu; phương án của câu hỏi nạp theo lô ({@code @BatchSize}). */
    @EntityGraph(attributePaths = "questions")
    List<MiniTask> findAllByClassIdOrderByCreatedAtAsc(UUID classId);

    @EntityGraph(attributePaths = "questions")
    List<MiniTask> findAllByClassIdInAndActiveTrueOrderByCreatedAtAsc(Collection<UUID> classIds);
}
