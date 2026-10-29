package com.eduerp.modules.attendance.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.attendance.AttendanceConstants;
import com.eduerp.modules.attendance.AttendanceEvents;
import com.eduerp.modules.attendance.AttendanceException;
import com.eduerp.modules.attendance.dto.AttendanceEntryRequest;
import com.eduerp.modules.attendance.dto.RecordAttendanceRequest;
import com.eduerp.modules.attendance.internal.access.ClassAttendanceAccessGuard;
import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import com.eduerp.modules.attendance.internal.rules.AttendanceRules;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điểm danh một buổi của một lớp: upsert theo {@code (classId, sessionDate)}. Chỉ giáo viên được phân
 * công dạy lớp (hoặc admin) mới gọi được - guard chạy TRƯỚC mọi kiểm tra khác.
 */
@Service
public class RecordAttendance {

    private final AttendanceSessionRepository sessions;
    private final EnrollmentManagement enrollments;
    private final ClassAttendanceAccessGuard guard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    RecordAttendance(AttendanceSessionRepository sessions, EnrollmentManagement enrollments,
            ClassAttendanceAccessGuard guard, ApplicationEventPublisher events) {
        this(sessions, enrollments, guard, events,
                Clock.system(ZoneId.of(AttendanceConstants.Schedules.CENTER_ZONE_ID)));
    }

    /** Constructor cho test: "hôm nay" phải chốt được để kiểm luật ngày tương lai. */
    RecordAttendance(AttendanceSessionRepository sessions, EnrollmentManagement enrollments,
            ClassAttendanceAccessGuard guard, ApplicationEventPublisher events, Clock clock) {
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.guard = guard;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, RecordAttendanceRequest request) {
        var classInfo = guard.requireAccessToClass(actorAccountId, request.classId(), AccessConstants.Actions.UPDATE);
        // Chỉ lớp đang học mới có buổi học: chưa khai giảng thì chưa học, kết thúc/huỷ thì đã đóng sổ.
        if (!classInfo.isInProgress()) {
            throw new AttendanceException.ClassNotInProgress(request.classId());
        }
        if (AttendanceRules.isFutureDate(request.sessionDate(), LocalDate.now(clock))) {
            throw new AttendanceException.FutureSessionDate(request.sessionDate());
        }
        if (request.sessionDate().isBefore(classInfo.startDate())) {
            throw new AttendanceException.BeforeClassStart(request.sessionDate(), classInfo.startDate());
        }
        requireEntriesBelongToClass(request.classId(), request.entries());

        var session = sessions.findByClassIdAndSessionDate(request.classId(), request.sessionDate())
                .orElseGet(() -> new AttendanceSession(request.classId(), request.sessionDate(), request.note(),
                        actorAccountId));
        session.changeNote(request.note());
        request.entries().forEach(entry -> session.mark(entry.enrollmentId(), entry.status(), entry.note()));
        var saved = sessions.save(session);

        events.publishEvent(new AttendanceEvents.AttendanceRecorded(saved.getId(), request.classId(),
                actorAccountId, actorBranchId));
        return saved.getId();
    }

    /** Mọi học viên trong buổi phải thuộc đúng lớp đó, và không ai xuất hiện hai lần. */
    private void requireEntriesBelongToClass(UUID classId, List<AttendanceEntryRequest> entries) {
        Set<UUID> roster = enrollments.listEnrollmentsByClass(classId).stream()
                .map(EnrollmentManagement.ClassRosterEntry::enrollmentId)
                .collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        for (var entry : entries) {
            if (!seen.add(entry.enrollmentId())) {
                throw new AttendanceException.DuplicateEntry(entry.enrollmentId());
            }
            if (!roster.contains(entry.enrollmentId())) {
                throw new AttendanceException.EnrollmentNotInClass(entry.enrollmentId(), classId);
            }
        }
    }
}
