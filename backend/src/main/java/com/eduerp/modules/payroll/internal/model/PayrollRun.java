package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.YearMonth;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Một kỳ lương là trọn một tháng dương lịch, mỗi tháng đúng một kỳ. */
@Entity
@Table(name = "payroll_runs", uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRun {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int year;

    @Column(nullable = false, updatable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollConstants.PayrollRunStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    public PayrollRun(int year, int month) {
        this.year = year;
        this.month = month;
        this.status = PayrollConstants.PayrollRunStatus.DRAFT;
    }

    public YearMonth getPeriod() {
        return YearMonth.of(year, month);
    }

    /** Gửi duyệt lại sau khi bị từ chối: lý do cũ đã xử lý xong, không còn hiện nữa. */
    public void submitForApproval() {
        this.status = PayrollConstants.PayrollRunStatus.PENDING_APPROVAL;
        this.rejectionReason = null;
    }

    public void approve() {
        this.status = PayrollConstants.PayrollRunStatus.APPROVED;
    }

    public void reject(String reason) {
        this.status = PayrollConstants.PayrollRunStatus.DRAFT;
        this.rejectionReason = reason;
    }
}
