package com.eduerp.modules.attendance.internal.repository;

import com.eduerp.modules.attendance.internal.model.AttendanceRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    /** Cổng học viên: mọi buổi của mọi lớp học viên đã ghi danh, nạp sẵn buổi (ngày) trong một câu. */
    @EntityGraph(attributePaths = "session")
    List<AttendanceRecord> findAllByEnrollmentIdIn(Collection<UUID> enrollmentIds);
}
