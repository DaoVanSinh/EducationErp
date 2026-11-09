package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollPeriodInFutureException;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.PayrollClock;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo kỳ lương nháp và sinh phiếu cho mọi giáo viên có gì để trả trong tháng. Tháng đang diễn ra vẫn tạo được để
 * xem trước, nhưng chỉ gửi duyệt được sau khi tháng kết thúc ({@code SubmitPayrollRunForApproval}).
 */
@Service
public class CreatePayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipSynchronizer synchronizer;
    private final PayrollClock clock;

    CreatePayrollRun(PayrollRunRepository runs, PayslipSynchronizer synchronizer, PayrollClock clock) {
        this.runs = runs;
        this.synchronizer = synchronizer;
        this.clock = clock;
    }

    @Transactional
    public PayrollRunResponse execute(UUID actorAccountId, UUID actorBranchId, CreatePayrollRunRequest request) {
        var period = YearMonth.of(request.year(), request.month());
        if (period.atDay(1).isAfter(clock.today())) {
            throw new PayrollPeriodInFutureException(period);
        }
        if (runs.existsByYearAndMonth(request.year(), request.month())) {
            throw new PayrollRunAlreadyExistsException(request.year(), request.month());
        }
        var run = runs.save(new PayrollRun(request.year(), request.month()));
        return PayrollResponseAssembler.toRunResponse(run, synchronizer.synchronize(run));
    }
}
