package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Phần duy nhất kế toán được nhập trên phiếu: Thưởng/điều chỉnh, dương là cộng, âm là trừ. Giờ dạy và thuế do hệ
 * thống tính, sai giờ thì sửa điểm danh rồi tính lại.
 */
public record UpdatePayslipRequest(@NotNull BigDecimal adjustment,
        @Size(max = PayrollConstants.Limits.MAX_NOTE_LENGTH) String note) {
}
