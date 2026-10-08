package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class InstallmentLimitExceededException extends BillingException {
    public InstallmentLimitExceededException(UUID enrollmentId, int maxInstallments) {
        super("BILLING_INSTALLMENT_LIMIT_EXCEEDED", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " đã có đủ " + maxInstallments + " đợt thu");
    }

    private InstallmentLimitExceededException(String message) {
        super("BILLING_INSTALLMENT_LIMIT_EXCEEDED", HttpStatus.CONFLICT, message);
    }

    /** Spec mục 6: combo dùng LẠI lỗi này (cùng errorCode - bản chất giống hệt: hết số đợt được
     * phép), chỉ đổi đơn vị neo trong câu chữ từ "Ghi danh" sang "Combo". */
    public static InstallmentLimitExceededException forCombo(UUID comboId, int maxInstallments) {
        return new InstallmentLimitExceededException(
                "Combo " + comboId + " đã có đủ " + maxInstallments + " đợt thu");
    }
}
