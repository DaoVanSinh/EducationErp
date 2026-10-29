package com.eduerp.modules.attendance.internal.model;

import com.eduerp.modules.attendance.AttendanceConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Trạng thái của một lượt ghi danh ở một buổi điểm danh. {@code enrollmentId} là UUID trần (rule #3). */
@Entity
@Table(name = "attendance_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private AttendanceSession session;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceConstants.AttendanceStatus status;

    @Column(length = AttendanceConstants.Limits.MAX_NOTE_LENGTH)
    private String note;

    AttendanceRecord(AttendanceSession session, UUID enrollmentId, AttendanceConstants.AttendanceStatus status,
            String note) {
        this.session = session;
        this.enrollmentId = enrollmentId;
        this.status = status;
        this.note = note;
    }

    void change(AttendanceConstants.AttendanceStatus status, String note) {
        this.status = status;
        this.note = note;
    }
}
