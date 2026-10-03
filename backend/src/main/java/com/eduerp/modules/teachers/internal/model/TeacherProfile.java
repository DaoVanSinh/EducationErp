package com.eduerp.modules.teachers.internal.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "teacher_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "teacher_profile_subjects", joinColumns = @JoinColumn(name = "teacher_profile_id"))
    @Column(name = "subject", nullable = false)
    private final List<String> subjects = new ArrayList<>();

    @Setter
    private String phone;

    @Setter
    private String bio;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public TeacherProfile(UUID accountId, List<String> subjects, String phone, String bio) {
        this.accountId = accountId;
        this.subjects.addAll(subjects);
        this.phone = phone;
        this.bio = bio;
        this.active = true;
    }

    public List<String> getSubjects() {
        return List.copyOf(subjects);
    }

    public void setSubjects(List<String> subjects) {
        this.subjects.clear();
        this.subjects.addAll(subjects);
    }
}
