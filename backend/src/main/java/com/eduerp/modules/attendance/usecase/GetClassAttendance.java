package com.eduerp.modules.attendance.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.attendance.AttendanceConstants;
import com.eduerp.modules.attendance.dto.AttendanceSessionResponse;
import com.eduerp.modules.attendance.dto.ClassAttendanceResponse;
import com.eduerp.modules.attendance.dto.StudentAttendanceRow;
import com.eduerp.modules.attendance.internal.access.ClassAttendanceAccessGuard;
import com.eduerp.modules.attendance.internal.model.AttendanceRecord;
import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import com.eduerp.modules.attendance.internal.rules.AttendanceRules;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng điểm danh của một lớp: các buổi (ngày tăng dần) × học viên, kèm tỷ lệ chuyên cần từng người và
 * thống kê từng buổi. Số lần đọc cố định, không tăng theo số học viên/số buổi: guard, danh sách lớp,
 * các buổi kèm records (một câu), tên học viên theo lô.
 */
@Service
public class GetClassAttendance {

    private final AttendanceSessionRepository sessions;
    private final EnrollmentManagement enrollments;
    private final StudentsManagement students;
    private final ClassAttendanceAccessGuard guard;

    GetClassAttendance(AttendanceSessionRepository sessions, EnrollmentManagement enrollments,
            StudentsManagement students, ClassAttendanceAccessGuard guard) {
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.students = students;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public ClassAttendanceResponse execute(UUID actorAccountId, UUID classId) {
        guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listEnrollmentsByClass(classId);
        var classSessions = sessions.findAllByClassIdOrderBySessionDateAsc(classId);
        var names = students.displayNamesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());

        var sessionResponses = classSessions.stream().map(GetClassAttendance::toSessionResponse).toList();
        var recordsByEnrollment = recordsByEnrollment(classSessions);
        var rows = roster.stream()
                .map(entry -> toRow(entry, names.get(entry.studentProfileId()),
                        recordsByEnrollment.getOrDefault(entry.enrollmentId(), List.of())))
                .toList();
        return new ClassAttendanceResponse(classId, sessionResponses, rows);
    }

    private static AttendanceSessionResponse toSessionResponse(AttendanceSession session) {
        var statuses = session.getRecords().stream().map(AttendanceRecord::getStatus).toList();
        return new AttendanceSessionResponse(session.getId(), session.getSessionDate(), session.getNote(),
                AttendanceRules.summarize(statuses));
    }

    private static Map<UUID, List<AttendanceRecord>> recordsByEnrollment(List<AttendanceSession> classSessions) {
        var result = new HashMap<UUID, List<AttendanceRecord>>();
        classSessions.forEach(session -> session.getRecords().forEach(record ->
                result.computeIfAbsent(record.getEnrollmentId(), key -> new ArrayList<>()).add(record)));
        return result;
    }

    private static StudentAttendanceRow toRow(EnrollmentManagement.ClassRosterEntry entry, String studentName,
            List<AttendanceRecord> records) {
        var statuses = new HashMap<UUID, AttendanceConstants.AttendanceStatus>();
        var notes = new HashMap<UUID, String>();
        records.forEach(record -> {
            statuses.put(record.getSession().getId(), record.getStatus());
            if (record.getNote() != null) {
                notes.put(record.getSession().getId(), record.getNote());
            }
        });
        return new StudentAttendanceRow(entry.enrollmentId(), entry.studentProfileId(), studentName,
                entry.status(), statuses, notes, AttendanceRules.summarize(statuses.values()));
    }
}
