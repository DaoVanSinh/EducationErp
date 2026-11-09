package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import com.eduerp.modules.payroll.internal.rules.PayslipFigures;
import com.eduerp.modules.teachers.TeachersConstants;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Ảnh chụp lương của một giáo viên trong một kỳ: đơn giá, số giờ, số người phụ thuộc đều lưu ở đây, nên đổi điều
 * khoản lương về sau không làm đổi kỳ đã duyệt.
 */
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
    @Column(name = "pay_type", nullable = false)
    private TeachersConstants.PayType payType;

    /** Lương tháng hoặc đơn giá giờ tại thời điểm tính. */
    @Column(name = "pay_rate", nullable = false)
    private BigDecimal payRate;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "hours_worked")
    private BigDecimal hoursWorked;

    @Column(name = "paid_days", nullable = false)
    private int paidDays;

    @Column(name = "probation_days", nullable = false)
    private int probationDays;

    @Column(name = "dependent_count", nullable = false)
    private int dependentCount;

    @Column(name = "gross_pay", nullable = false)
    private BigDecimal grossPay;

    @Column(name = "allowances_total", nullable = false)
    private BigDecimal allowancesTotal;

    @Column(name = "adjustment", nullable = false)
    private BigDecimal adjustment = BigDecimal.ZERO;

    @Column(name = "social_insurance_employee", nullable = false)
    private BigDecimal socialInsuranceEmployee;

    @Column(name = "social_insurance_employer", nullable = false)
    private BigDecimal socialInsuranceEmployer;

    @Column(name = "income_tax_withheld", nullable = false)
    private BigDecimal incomeTaxWithheld;

    @Column(length = PayrollConstants.Limits.MAX_NOTE_LENGTH)
    private String note;

    @Column(name = "in_probation", nullable = false)
    private boolean inProbation;

    /** Chi nhánh chủ quản lúc chốt phiếu: giáo viên chuyển chi nhánh sau đó thì phiếu cũ vẫn do chi nhánh cũ trả. */
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PayrollConstants.PaymentMethod paymentMethod;

    @Column(name = "paid_by_account_id")
    private UUID paidByAccountId;

    @Column(name = "payment_note", length = PayrollConstants.Limits.MAX_PAYMENT_NOTE_LENGTH)
    private String paymentNote;

    public Payslip(PayrollRun payrollRun, UUID accountId, PayslipFigures figures) {
        this.payrollRun = payrollRun;
        this.accountId = accountId;
        apply(figures);
    }

    /** Ghi kết quả tính lại. Thưởng/điều chỉnh và ghi chú do kế toán nhập nên giữ nguyên. */
    public final void apply(PayslipFigures figures) {
        this.payType = figures.payType();
        this.payRate = figures.payRate();
        this.sessionCount = figures.sessionCount();
        this.hoursWorked = figures.hoursWorked();
        this.paidDays = figures.paidDays();
        this.probationDays = figures.probationDays();
        this.dependentCount = figures.dependentCount();
        this.grossPay = figures.grossPay();
        this.allowancesTotal = figures.allowancesTotal();
        this.socialInsuranceEmployee = figures.socialInsuranceEmployee();
        this.socialInsuranceEmployer = figures.socialInsuranceEmployer();
        this.incomeTaxWithheld = figures.incomeTaxWithheld();
        this.inProbation = figures.probationDays() > 0;
    }

    /** Đổi điều chỉnh làm đổi thu nhập chịu thuế, nên thuế tính lại cùng lúc. */
    public void changeAdjustment(BigDecimal adjustment, String note) {
        this.adjustment = adjustment;
        this.note = note;
        this.incomeTaxWithheld = PayrollRules.incomeTax(payType, grossPay, allowancesTotal, adjustment,
                socialInsuranceEmployee, dependentCount);
    }

    public void assignBranch(UUID branchId) {
        this.branchId = branchId;
    }

    public boolean isPaid() {
        return paidOn != null;
    }

    public void markPaid(LocalDate paidOn, PayrollConstants.PaymentMethod method, UUID paidByAccountId, String note) {
        this.paidOn = paidOn;
        this.paymentMethod = method;
        this.paidByAccountId = paidByAccountId;
        this.paymentNote = note;
    }

    /** Lý do được giữ ở {@code paymentNote} để phiếu còn dấu vết vì sao trở lại chờ thanh toán. */
    public void undoPayment(String reason) {
        this.paidOn = null;
        this.paymentMethod = null;
        this.paidByAccountId = null;
        this.paymentNote = reason;
    }

    public BigDecimal getNetPay() {
        return PayrollRules.netPay(grossPay, allowancesTotal, adjustment, socialInsuranceEmployee, incomeTaxWithheld);
    }
}
