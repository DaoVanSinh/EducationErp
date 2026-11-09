package com.eduerp.modules.payroll.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import com.eduerp.modules.payroll.PayslipNotPayableException;
import com.eduerp.modules.payroll.dto.MarkPayslipPaidRequest;
import com.eduerp.modules.payroll.internal.PayrollClock;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin chi nhánh đánh dấu đã trả đủ một phiếu của chi nhánh mình. Số tiền đã chốt lúc duyệt, ở đây không sửa được. */
@Service
public class MarkPayslipPaid {

    private final PayslipRepository payslips;
    private final AccessManagement access;
    private final PayrollClock clock;
    private final ApplicationEventPublisher events;

    MarkPayslipPaid(PayslipRepository payslips, AccessManagement access, PayrollClock clock,
            ApplicationEventPublisher events) {
        this.payslips = payslips;
        this.access = access;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public void execute(UUID payslipId, UUID actorAccountId, UUID actorBranchId, MarkPayslipPaidRequest request) {
        var payslip = payslips.findById(payslipId).orElseThrow(() -> new PayslipNotFoundException(payslipId));
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.PAYROLL, AccessConstants.Actions.UPDATE)
                .requireCovers(payslip.getBranchId());
        if (payslip.getPayrollRun().getStatus() != PayrollConstants.PayrollRunStatus.APPROVED) {
            throw new PayslipNotPayableException("Kỳ lương chưa được duyệt nên chưa chi trả được");
        }
        if (payslip.isPaid()) {
            throw new PayslipNotPayableException("Phiếu lương này đã được đánh dấu thanh toán");
        }
        if (request.paidOn().isAfter(clock.today())) {
            throw new AppValidationException("paidOn", "Ngày trả không được ở tương lai");
        }
        payslip.markPaid(request.paidOn(), request.method(), actorAccountId, request.note());
        events.publishEvent(new PayrollEvents.PayslipPaid(payslipId, actorAccountId, actorBranchId));
    }
}
