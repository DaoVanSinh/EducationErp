package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import com.eduerp.modules.payroll.PayslipNotPayableException;
import com.eduerp.modules.payroll.dto.UndoPayslipPaymentRequest;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chỉ admin gốc huỷ được đánh dấu nhầm (rule ở controller), lý do được giữ lại trên phiếu. */
@Service
public class UndoPayslipPayment {

    private final PayslipRepository payslips;
    private final ApplicationEventPublisher events;

    UndoPayslipPayment(PayslipRepository payslips, ApplicationEventPublisher events) {
        this.payslips = payslips;
        this.events = events;
    }

    @Transactional
    public void execute(UUID payslipId, UUID actorAccountId, UUID actorBranchId, UndoPayslipPaymentRequest request) {
        var payslip = payslips.findById(payslipId).orElseThrow(() -> new PayslipNotFoundException(payslipId));
        if (!payslip.isPaid()) {
            throw new PayslipNotPayableException("Phiếu lương này chưa được đánh dấu thanh toán");
        }
        payslip.undoPayment(request.reason().strip());
        events.publishEvent(new PayrollEvents.PayslipPaymentUndone(payslipId, actorAccountId, actorBranchId));
    }
}
