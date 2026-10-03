package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String description;

    @Setter
    private Integer standardSessionCount;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public Course(String code, String name, String description, Integer standardSessionCount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.standardSessionCount = standardSessionCount;
        this.active = true;
    }
}
