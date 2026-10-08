package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePayslip {

    private final PayslipRepository payslips;
    private final EmploymentContractRepository contracts;

    UpdatePayslip(PayslipRepository payslips, EmploymentContractRepository contracts) {
        this.payslips = payslips;
        this.contracts = contracts;
    }

    @Transactional
    public PayslipResponse execute(UUID payslipId, UUID actorAccountId, UUID actorBranchId,
            UpdatePayslipRequest request) {
        var payslip = payslips.findById(payslipId).orElseThrow(() -> new PayslipNotFoundException(payslipId));
        var run = payslip.getPayrollRun();
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(run.getId());
        }

        if (payslip.getContractType() == PayrollConstants.ContractType.COLLABORATOR && request.hoursWorked() != null) {
            // Hợp đồng dùng để tính lại grossPay là hợp đồng ACTIVE của chính account này - nếu hợp
            // đồng đã bị kết thúc giữa lúc tạo kỳ lương và lúc sửa phiếu, đây là lỗi dữ liệu thật
            // (không phải "không tìm thấy hợp đồng theo id" theo nghĩa thông thường, nhưng tái dùng
            // ContractNotFoundException vì cùng bản chất 404 - hợp đồng không còn tồn tại ở trạng thái
            // cần để tính lương).
            var contract = contracts.findByAccountIdAndStatus(payslip.getAccountId(),
                    PayrollConstants.ContractStatus.ACTIVE).orElseThrow(
                            () -> new ContractNotFoundException(payslip.getAccountId()));
            var payPeriodStart = LocalDate.of(run.getYear(), run.getMonth(), 1);
            var grossPay = PayrollRules.baseGrossPay(contract, payPeriodStart, request.hoursWorked());
            payslip.setHoursWorked(request.hoursWorked());
            payslip.setGrossPay(grossPay);
            payslip.setSocialInsuranceEmployee(
                    PayrollRules.socialInsuranceEmployeeShare(grossPay, payslip.getContractType()));
            payslip.setSocialInsuranceEmployer(
                    PayrollRules.socialInsuranceEmployerShare(grossPay, payslip.getContractType()));
        }
        if (request.incomeTaxWithheld() != null) {
            payslip.setIncomeTaxWithheld(request.incomeTaxWithheld());
        }
        if (request.note() != null) {
            payslip.setNote(request.note());
        }

        var netPay = PayrollRules.netPay(payslip.getGrossPay(), payslip.getAllowancesTotal(),
                payslip.getSocialInsuranceEmployee(), payslip.getIncomeTaxWithheld());
        return new PayslipResponse(payslip.getId(), payslip.getAccountId(), null, payslip.getContractType(),
                payslip.getGrossPay(), payslip.getSocialInsuranceEmployee(), payslip.getSocialInsuranceEmployer(),
                payslip.getIncomeTaxWithheld(), netPay, payslip.getHoursWorked(), payslip.isInProbation());
    }
}
