package com.eduerp.modules.payroll.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** @ElementCollection, không có PK riêng - mirror teacher_profile_subjects (Phase 2.5), shape chuẩn. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAllowance {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    public ContractAllowance(String name, BigDecimal amount) {
        this.name = name;
        this.amount = amount;
    }
}
