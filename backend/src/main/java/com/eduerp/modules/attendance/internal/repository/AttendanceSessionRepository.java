package com.eduerp.modules.attendance.internal.repository;

import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    /** Upsert của {@code RecordAttendance} - UNIQUE (class_id, session_date) bảo đảm tối đa một dòng. */
    @EntityGraph(attributePaths = "records")
    Optional<AttendanceSession> findByClassIdAndSessionDate(UUID classId, LocalDate sessionDate);

    /** Cả bảng điểm danh của lớp trong MỘT câu, nạp sẵn records - không N+1 theo số buổi. */
    @EntityGraph(attributePaths = "records")
    List<AttendanceSession> findAllByClassIdOrderBySessionDateAsc(UUID classId);

    @Query("""
            SELECT s.classId AS classId, COUNT(s) AS sessionCount, COALESCE(SUM(s.durationMinutes), 0) AS totalMinutes,
                   MIN(s.sessionDate) AS firstSessionDate, MAX(s.sessionDate) AS lastSessionDate
            FROM AttendanceSession s
            WHERE s.teacherAccountId = :teacherAccountId
            GROUP BY s.classId
            ORDER BY MAX(s.sessionDate) DESC
            """)
    List<TaughtClassRow> summarizeTaughtClasses(@Param("teacherAccountId") UUID teacherAccountId);

    @Query("""
            SELECT s.teacherAccountId AS teacherAccountId, COUNT(s) AS sessionCount,
                   COALESCE(SUM(s.durationMinutes), 0) AS totalMinutes
            FROM AttendanceSession s
            WHERE s.sessionDate BETWEEN :from AND :to
            GROUP BY s.teacherAccountId
            """)
    List<TeachingSummaryRow> summarizeTeaching(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
