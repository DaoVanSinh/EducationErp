package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.shared.PageResponse;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPayrollRuns {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;

    ListPayrollRuns(PayrollRunRepository runs, PayslipRepository payslips) {
        this.runs = runs;
        this.payslips = payslips;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollRunResponse> execute(Pageable pageable) {
        var page = runs.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    private PayrollRunResponse toResponse(PayrollRun run) {
        var runPayslips = payslips.findAllByPayrollRun_Id(run.getId());
        var totalGross = runPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                runPayslips.size(), totalGross);
    }
}
