package com.eduerp.modules.students.internal.model;

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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "student_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    @Setter
    private LocalDate dateOfBirth;

    @Setter
    private String phone;

    @Setter
    private String sourceChannel;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public StudentProfile(UUID accountId, LocalDate dateOfBirth, String phone, String sourceChannel) {
        this.accountId = accountId;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.sourceChannel = sourceChannel;
        this.active = true;
    }
}
