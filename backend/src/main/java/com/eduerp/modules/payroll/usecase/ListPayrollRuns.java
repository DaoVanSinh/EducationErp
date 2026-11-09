package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunAggregateRow;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import com.eduerp.shared.PageResponse;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * Review finding Important #6: trước đây gọi payslips.findAllByPayrollRun_Id riêng cho từng dòng
     * của trang (N+1 - 21 query cho 20 dòng). Giờ một query gộp duy nhất cho cả trang, mirror
     * {@code AccountRoleAssignmentRepository.countAccountsByRoleForAccounts} mà dự án đã dùng.
     */
    @Transactional(readOnly = true)
    public PageResponse<PayrollRunResponse> execute(Pageable pageable) {
        var page = runs.findAll(pageable);
        var runIds = page.getContent().stream().map(PayrollRun::getId).toList();
        var aggregates = payslips.aggregateByPayrollRunIds(runIds).stream()
                .collect(Collectors.toMap(PayrollRunAggregateRow::getPayrollRunId, Function.identity()));
        return PageResponse.of(page.map(run -> PayrollResponseAssembler.toRunResponse(run, aggregates.get(run.getId()))));
    }
}
