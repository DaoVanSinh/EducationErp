package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    /** Học phí toàn khoá, VND, scale 0. Nullable - khoá học có thể chưa chốt giá (spec mục 5). */
    @Setter
    @Column(name = "tuition_fee")
    private BigDecimal tuitionFee;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public Course(String code, String name, String description, Integer standardSessionCount,
            BigDecimal tuitionFee) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.standardSessionCount = standardSessionCount;
        this.tuitionFee = tuitionFee;
        this.active = true;
    }
}
