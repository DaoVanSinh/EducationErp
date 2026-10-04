package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #1: gộp ghi danh của hai học viên (hoặc hai chi nhánh) vào một combo phải bị chặn,
 * KHÔNG được âm thầm lấy học viên của ghi danh đầu tiên rồi thu tiền người khác. */
public final class StudentMismatchInComboException extends BillingException {
    public StudentMismatchInComboException(UUID enrollmentId) {
        super("BILLING_COMBO_STUDENT_MISMATCH", HttpStatus.BAD_REQUEST,
                "Ghi danh " + enrollmentId + " không cùng học viên/chi nhánh với các ghi danh còn lại");
    }
}
