package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "employment_contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentContract {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, updatable = false)
    private PayrollConstants.ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollConstants.ContractStatus status;

    @Setter
    @Column(name = "base_salary")
    private BigDecimal baseSalary;

    @Setter
    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "probation_start_date", updatable = false)
    private LocalDate probationStartDate;

    @Setter
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Setter
    @Column(name = "contract_file_key", length = 512)
    private String contractFileKey;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "contract_allowances", joinColumns = @JoinColumn(name = "contract_id"))
    private final List<ContractAllowance> allowances = new ArrayList<>();

    public EmploymentContract(UUID accountId, PayrollConstants.ContractType contractType, BigDecimal baseSalary,
            BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate,
            List<ContractAllowance> allowances) {
        this.accountId = accountId;
        this.contractType = contractType;
        this.baseSalary = baseSalary;
        this.hourlyRate = hourlyRate;
        this.probationStartDate = probationStartDate;
        this.probationEndDate = probationEndDate;
        this.startDate = startDate;
        this.status = PayrollConstants.ContractStatus.ACTIVE;
        this.allowances.addAll(allowances);
    }

    public List<ContractAllowance> getAllowances() {
        return List.copyOf(allowances);
    }

    public void setAllowances(List<ContractAllowance> allowances) {
        this.allowances.clear();
        this.allowances.addAll(allowances);
    }

    /** Review Focus #5: kết thúc hợp đồng lao động độc lập với trạng thái tài khoản đăng nhập -
     * không có logic nào ở đây hay nơi gọi tham chiếu Account.status. */
    public void terminate() {
        this.status = PayrollConstants.ContractStatus.TERMINATED;
        this.endDate = LocalDate.now();
    }
}
