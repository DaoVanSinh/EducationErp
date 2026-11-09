package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.Payslip;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findAllByPayrollRun_Id(UUID payrollRunId);

    /**
     * Tab "Lương" của trang Hồ sơ của tôi: chỉ kỳ ở {@code status} đã duyệt, kỳ mới nhất trước. Entity graph nạp kèm
     * {@code payrollRun} vì mỗi dòng cần year/month của nó (mirror {@code ClassRepository.findAll}).
     */
    @EntityGraph(attributePaths = "payrollRun")
    Page<Payslip> findAllByAccountIdAndPayrollRun_StatusOrderByPayrollRun_YearDescPayrollRun_MonthDesc(UUID accountId,
            PayrollConstants.PayrollRunStatus status, Pageable pageable);

    /**
     * Phiếu đã duyệt để chi trả, xuyên nhiều kỳ. Các bộ lọc đều tuỳ chọn; {@code branchId} null chỉ xảy ra với admin
     * gốc, vì admin chi nhánh đã bị ép về chi nhánh của mình ở use case.
     */
    @EntityGraph(attributePaths = "payrollRun")
    @Query("""
            SELECT p FROM Payslip p
            WHERE p.payrollRun.status = :status
              AND (:branchId IS NULL OR p.branchId = :branchId)
              AND (:year IS NULL OR p.payrollRun.year = :year)
              AND (:month IS NULL OR p.payrollRun.month = :month)
              AND (:paid IS NULL OR (:paid = TRUE AND p.paidOn IS NOT NULL) OR (:paid = FALSE AND p.paidOn IS NULL))
            ORDER BY p.payrollRun.year DESC, p.payrollRun.month DESC, p.id
            """)
    Page<Payslip> searchApproved(@Param("status") PayrollConstants.PayrollRunStatus status,
            @Param("branchId") UUID branchId, @Param("year") Integer year, @Param("month") Integer month,
            @Param("paid") Boolean paid, Pageable pageable);

    /**
     * Đếm/cộng dồn theo lô cho cả trang thay vì một query riêng cho từng kỳ lương (N+1) - mirror
     * {@code AccountRoleAssignmentRepository.countAccountsByRoleForAccounts}. Xuất phát FROM Payslip
     * nên một kỳ lương chưa có phiếu nào tự nhiên không xuất hiện trong kết quả; nơi gọi (usecase) lấp
     * khoảng trống đó bằng count=0/total=0.
     */
    @Query("""
            SELECT p.payrollRun.id AS payrollRunId, COUNT(p) AS payslipCount,
                   COALESCE(SUM(p.grossPay), 0) AS totalGrossPay,
                   COALESCE(SUM(p.grossPay + p.allowancesTotal + p.adjustment - p.socialInsuranceEmployee
                                - p.incomeTaxWithheld), 0) AS totalNetPay,
                   COALESCE(SUM(p.socialInsuranceEmployer), 0) AS totalEmployerInsurance
            FROM Payslip p
            WHERE p.payrollRun.id IN :payrollRunIds
            GROUP BY p.payrollRun.id
            """)
    List<PayrollRunAggregateRow> aggregateByPayrollRunIds(@Param("payrollRunIds") Collection<UUID> payrollRunIds);
}
