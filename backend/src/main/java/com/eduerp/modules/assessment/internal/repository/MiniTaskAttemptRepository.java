package com.eduerp.modules.assessment.internal.repository;

import com.eduerp.modules.assessment.internal.model.MiniTaskAttempt;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiniTaskAttemptRepository extends JpaRepository<MiniTaskAttempt, UUID> {

    /** Thống kê theo lớp: mọi lượt làm của mọi mini-task của lớp trong một câu IN. */
    List<MiniTaskAttempt> findAllByMiniTaskIdIn(Collection<UUID> miniTaskIds);

    /** Cổng học viên: mọi lượt làm của các ghi danh của chính học viên, cũ trước mới sau. */
    List<MiniTaskAttempt> findAllByEnrollmentIdInOrderBySubmittedAtAsc(Collection<UUID> enrollmentIds);
}
