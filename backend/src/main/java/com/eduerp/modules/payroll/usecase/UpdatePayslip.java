package com.eduerp.modules.payroll.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập Thưởng/điều chỉnh của một phiếu ở kỳ nháp. Khoản khác 0 phải có lý do: đây là con số duy nhất do người nhập
 * trên phiếu, người duyệt cần biết vì sao có nó.
 */
@Service
public class UpdatePayslip {

    private final PayslipRepository payslips;

    UpdatePayslip(PayslipRepository payslips) {
        this.payslips = payslips;
    }

    @Transactional
    public PayslipResponse execute(UUID payrollRunId, UUID payslipId, UUID actorAccountId, UUID actorBranchId,
            UpdatePayslipRequest request) {
        var payslip = payslips.findById(payslipId)
                .filter(found -> found.getPayrollRun().getId().equals(payrollRunId))
                .orElseThrow(() -> new PayslipNotFoundException(payslipId));
        if (payslip.getPayrollRun().getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(payrollRunId);
        }
        var note = request.note() == null ? null : request.note().strip();
        if (request.adjustment().signum() != 0 && (note == null || note.isEmpty())) {
            throw new AppValidationException("note", "Nhập lý do của khoản thưởng/điều chỉnh");
        }
        payslip.changeAdjustment(request.adjustment(), note);
        if (payslip.getNetPay().signum() < 0) {
            throw new AppValidationException("adjustment", "Khoản trừ làm thực nhận âm");
        }
        return PayrollResponseAssembler.toPayslipResponse(payslip, Map.of(), Map.of());
    }
}
