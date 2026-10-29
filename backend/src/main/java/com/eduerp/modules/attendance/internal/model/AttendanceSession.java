package com.eduerp.modules.attendance.internal.model;

import com.eduerp.modules.attendance.AttendanceConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một buổi điểm danh của một lớp vào một ngày. {@code classId}/{@code createdByAccountId} là UUID trần
 * (rule #3 - courses/identity là module khác). {@code records} là quan hệ JPA thật vì cùng module.
 */
@Entity
@Table(name = "attendance_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceSession {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "session_date", nullable = false, updatable = false)
    private LocalDate sessionDate;

    @Column(length = AttendanceConstants.Limits.MAX_NOTE_LENGTH)
    private String note;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceRecord> records = new ArrayList<>();

    public AttendanceSession(UUID classId, LocalDate sessionDate, String note, UUID createdByAccountId) {
        this.classId = classId;
        this.sessionDate = sessionDate;
        this.note = note;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeNote(String note) {
        this.note = note;
        this.updatedAt = Instant.now();
    }

    /** Ghi trạng thái của một học viên: đã có dòng thì SỬA (UNIQUE session + enrollment của V28). */
    public void mark(UUID enrollmentId, AttendanceConstants.AttendanceStatus status, String note) {
        records.stream()
                .filter(record -> record.getEnrollmentId().equals(enrollmentId))
                .findFirst()
                .ifPresentOrElse(record -> record.change(status, note),
                        () -> records.add(new AttendanceRecord(this, enrollmentId, status, note)));
        this.updatedAt = Instant.now();
    }
}
