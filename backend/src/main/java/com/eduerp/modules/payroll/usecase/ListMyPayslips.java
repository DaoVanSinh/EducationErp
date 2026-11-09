package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.MyPayslipResponse;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiếu lương CỦA CHÍNH người gọi, chỉ từ kỳ đã duyệt: phiếu kỳ nháp còn đổi được, cho xem sớm dễ gây hiểu nhầm về
 * số tiền thực nhận. KHÔNG đòi {@code READ_PAYROLL}: {@code accountId} luôn là chính người gọi (ép ở
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
        var page = payslips.findAllByAccountIdAndPayrollRun_StatusOrderByPayrollRun_YearDescPayrollRun_MonthDesc(
                accountId, PayrollConstants.PayrollRunStatus.APPROVED, pageable);
        return PageResponse.of(page.map(PayrollResponseAssembler::toMyPayslipResponse));
    }
}
