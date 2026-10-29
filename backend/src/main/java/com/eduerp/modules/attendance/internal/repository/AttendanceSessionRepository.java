package com.eduerp.modules.attendance.internal.repository;

import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    /** Upsert của {@code RecordAttendance} - UNIQUE (class_id, session_date) bảo đảm tối đa một dòng. */
    @EntityGraph(attributePaths = "records")
    Optional<AttendanceSession> findByClassIdAndSessionDate(UUID classId, LocalDate sessionDate);

    /** Cả bảng điểm danh của lớp trong MỘT câu, nạp sẵn records - không N+1 theo số buổi. */
    @EntityGraph(attributePaths = "records")
    List<AttendanceSession> findAllByClassIdOrderBySessionDateAsc(UUID classId);
}
