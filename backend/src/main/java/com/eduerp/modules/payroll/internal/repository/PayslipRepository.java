package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.internal.model.Payslip;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findAllByPayrollRun_Id(UUID payrollRunId);
}
