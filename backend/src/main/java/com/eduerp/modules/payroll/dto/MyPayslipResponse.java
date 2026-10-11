package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.util.UUID;

/** Một phiếu lương trong tab "Lương" của trang Hồ sơ của tôi. {@code year}/{@code month} đi kèm vì
 * đây là dữ liệu XUYÊN NHIỀU kỳ lương - khác {@code PayslipResponse} (luôn đọc trong ngữ cảnh MỘT
 * kỳ lương đã biết qua URL, nên không cần lặp lại year/month). */
public record MyPayslipResponse(UUID id, int year, int month, PayrollConstants.ContractType contractType,
        BigDecimal grossPay, BigDecimal netPay, boolean inProbation) {
}
