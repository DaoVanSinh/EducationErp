package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Kỳ lương không có phiếu lương nào thì không được gửi duyệt: mỗi tháng chỉ có MỘT kỳ lương
 * ({@link PayrollRunAlreadyExistsException}), duyệt một kỳ rỗng là khoá luôn tháng đó, giáo viên ký
 * hợp đồng muộn hơn không bao giờ được tính lương tháng ấy nữa.
 */
public final class PayrollRunEmptyException extends PayrollException {
    public PayrollRunEmptyException(UUID payrollRunId) {
        super("PAYROLL_RUN_EMPTY", HttpStatus.CONFLICT,
                "Kỳ lương " + payrollRunId + " chưa có phiếu lương nào, không thể gửi duyệt");
    }
}
