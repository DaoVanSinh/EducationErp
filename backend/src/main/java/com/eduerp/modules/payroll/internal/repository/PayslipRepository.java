package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.internal.model.Payslip;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findAllByPayrollRun_Id(UUID payrollRunId);

    /**
     * Đếm/cộng dồn theo lô cho cả trang thay vì một query riêng cho từng kỳ lương (N+1) - mirror
     * {@code AccountRoleAssignmentRepository.countAccountsByRoleForAccounts}. Xuất phát FROM Payslip
     * nên một kỳ lương chưa có phiếu nào tự nhiên không xuất hiện trong kết quả; nơi gọi (usecase) lấp
     * khoảng trống đó bằng count=0/total=0.
     */
    @Query("""
            SELECT p.payrollRun.id AS payrollRunId, COUNT(p) AS payslipCount,
                   COALESCE(SUM(p.grossPay), 0) AS totalGrossPay
            FROM Payslip p
            WHERE p.payrollRun.id IN :payrollRunIds
            GROUP BY p.payrollRun.id
            """)
    List<PayrollRunAggregateRow> aggregateByPayrollRunIds(@Param("payrollRunIds") Collection<UUID> payrollRunIds);
}
