package com.eduerp.modules.academic.internal.repository;

import com.eduerp.modules.academic.internal.model.GradeEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeEntryRepository extends JpaRepository<GradeEntry, UUID> {

    /** {@code RecordGrade} upsert theo cặp này - UNIQUE của V24 bảo đảm tối đa một dòng. */
    Optional<GradeEntry> findByEnrollmentIdAndCourseGradeColumnId(UUID enrollmentId,
            UUID courseGradeColumnId);

    /** Một câu IN cho CẢ lớp: bảng điểm 30 học viên không được bắn 30 câu SELECT (rule #3). */
    List<GradeEntry> findAllByEnrollmentIdIn(Collection<UUID> enrollmentIds);
}
