package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.dto.MyPayslipResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiếu lương CỦA CHÍNH người gọi, xuyên mọi kỳ lương - cho tab "Lương" của trang Hồ sơ của tôi.
 * KHÔNG đòi {@code READ_PAYROLL}: {@code accountId} luôn là chính người gọi (ép ở
 * {@code PayrollSelfServiceController}, không nhận từ query), nên không có cách nào xem được phiếu
 * lương của người khác qua đường này - mirror {@code GetCurrentSession}.
 */
@Service
public class ListMyPayslips {

    private final PayslipRepository payslips;

    ListMyPayslips(PayslipRepository payslips) {
        this.payslips = payslips;
    }

    @Transactional(readOnly = true)
    public PageResponse<MyPayslipResponse> execute(UUID accountId, Pageable pageable) {
        Page<Payslip> page = payslips.findAllByAccountId(accountId, pageable);
        return PageResponse.of(page.map(ListMyPayslips::toResponse));
    }

    private static MyPayslipResponse toResponse(Payslip payslip) {
        var netPay = PayrollRules.netPay(payslip.getGrossPay(), payslip.getAllowancesTotal(),
                payslip.getSocialInsuranceEmployee(), payslip.getIncomeTaxWithheld());
        return new MyPayslipResponse(payslip.getId(), payslip.getPayrollRun().getYear(),
                payslip.getPayrollRun().getMonth(), payslip.getContractType(), payslip.getGrossPay(), netPay,
                payslip.isInProbation());
    }
}
