package com.eduerp.modules.courses.internal.model;

import com.eduerp.modules.courses.CoursesConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "class_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassSchedule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id")
    private Class parentClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private CoursesConstants.DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    ClassSchedule(Class parentClass, CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.parentClass = parentClass;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
