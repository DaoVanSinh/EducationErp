package com.eduerp.modules.teachers.internal.model;

import com.eduerp.modules.teachers.TeachersConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Phần tử {@code @ElementCollection}, không có PK riêng - mirror {@code teacher_profile_subjects}. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TeacherAllowance {

    @Column(nullable = false, length = TeachersConstants.Limits.MAX_ALLOWANCE_NAME_LENGTH)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;
}
