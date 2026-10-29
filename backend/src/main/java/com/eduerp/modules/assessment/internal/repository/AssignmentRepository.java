package com.eduerp.modules.assessment.internal.repository;

import com.eduerp.modules.assessment.internal.model.Assignment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    /** Cả bảng theo dõi bài tập của lớp trong MỘT câu, nạp sẵn kết quả - không N+1 theo số bài. */
    @EntityGraph(attributePaths = "results")
    List<Assignment> findAllByClassIdOrderByCreatedAtAsc(UUID classId);

    /** Cổng học viên: các bài đang bật của mọi lớp học viên đã ghi danh. */
    @EntityGraph(attributePaths = "results")
    List<Assignment> findAllByClassIdInAndActiveTrueOrderByCreatedAtAsc(Collection<UUID> classIds);
}
