package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
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
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payslips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payslip {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** payroll_run là quan hệ JPA thật - PayrollRun cùng module (rule #3 chỉ cấm xuyên module). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, updatable = false)
    private PayrollConstants.ContractType contractType;

    @Setter
    @Column(name = "gross_pay", nullable = false)
    private BigDecimal grossPay;

    @Column(name = "allowances_total", nullable = false)
    private BigDecimal allowancesTotal;

    @Setter
    @Column(name = "social_insurance_employee", nullable = false)
    private BigDecimal socialInsuranceEmployee;

    @Setter
    @Column(name = "social_insurance_employer", nullable = false)
    private BigDecimal socialInsuranceEmployer;

    @Setter
    @Column(name = "income_tax_withheld", nullable = false)
    private BigDecimal incomeTaxWithheld = BigDecimal.ZERO;

    @Setter
    @Column(name = "hours_worked")
    private BigDecimal hoursWorked;

    @Setter
    @Column(length = 1000)
    private String note;

    @Column(name = "in_probation", nullable = false)
    private boolean inProbation;

    public Payslip(PayrollRun payrollRun, UUID accountId, PayrollConstants.ContractType contractType,
            BigDecimal grossPay, BigDecimal allowancesTotal, BigDecimal socialInsuranceEmployee,
            BigDecimal socialInsuranceEmployer, BigDecimal hoursWorked, boolean inProbation) {
        this.payrollRun = payrollRun;
        this.accountId = accountId;
        this.contractType = contractType;
        this.grossPay = grossPay;
        this.allowancesTotal = allowancesTotal;
        this.socialInsuranceEmployee = socialInsuranceEmployee;
        this.socialInsuranceEmployer = socialInsuranceEmployer;
        this.hoursWorked = hoursWorked;
        this.inProbation = inProbation;
        this.incomeTaxWithheld = BigDecimal.ZERO;
    }
}
