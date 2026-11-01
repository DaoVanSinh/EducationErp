package com.eduerp.modules.landing.internal.model;

import com.eduerp.modules.landing.LandingConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "landing_consultations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingConsultation {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    private String email;

    @Column(name = "target_course_id")
    private UUID targetCourseId;

    @Column(name = "target_branch_id")
    private UUID targetBranchId;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private LandingConstants.ConsultationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LandingConsultation(
            String fullName,
            String phone,
            String email,
            UUID targetCourseId,
            UUID targetBranchId,
            String notes) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.targetCourseId = targetCourseId;
        this.targetBranchId = targetBranchId;
        this.notes = notes;
        this.status = LandingConstants.ConsultationStatus.NEW;
        this.createdAt = Instant.now();
    }
}
