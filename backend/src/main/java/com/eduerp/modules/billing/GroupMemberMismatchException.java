package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Nhóm là nhiều người học CÙNG một khoá ở CÙNG một chi nhánh, mỗi người đếm một lần. Ghi danh khác khoá,
 * khác chi nhánh, hoặc của một học viên đã có trong nhóm đều làm sai số người dùng để tra bậc giảm.
 */
public final class GroupMemberMismatchException extends BillingException {
    public GroupMemberMismatchException(UUID enrollmentId) {
        super("BILLING_GROUP_MEMBER_MISMATCH", HttpStatus.BAD_REQUEST,
                "Ghi danh " + enrollmentId + " không cùng khoá, cùng chi nhánh, hoặc trùng học viên với nhóm");
    }
}
