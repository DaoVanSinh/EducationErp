package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Ai dạy một lớp từ ngày nào. Buổi ghi bù sau khi thay giáo viên vẫn tính giờ cho đúng người đã dạy hôm đó. */
@Entity
@Table(name = "class_teacher_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassTeacherAssignment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    public ClassTeacherAssignment(UUID classId, UUID teacherId, LocalDate effectiveFrom) {
        this.classId = classId;
        this.teacherId = teacherId;
        this.effectiveFrom = effectiveFrom;
    }

    public void reassign(UUID teacherId, LocalDate effectiveFrom) {
        this.teacherId = teacherId;
        this.effectiveFrom = effectiveFrom;
    }
}
